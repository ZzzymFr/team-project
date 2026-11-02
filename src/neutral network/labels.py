"""训练与去重共用的保守标签规范化；原始标签不覆盖。"""
import re

NORMALIZATION_VERSION = 2
TOKEN_RE = re.compile(r"\\[a-zA-Z]+|\\.|\S", re.S)
ALIASES = {r"\le": r"\leq", r"\ge": r"\geq", r"\ne": r"\neq", r"\to": r"\rightarrow",
           r"\Box": r"\square", "□": r"\square", "⟨": r"\langle", "⟩": r"\rangle",
           "⊥": r"\perp", "‖": r"\|", r"\Vert": r"\|", r"\lVert": r"\|", r"\rVert": r"\|"}
LABEL_RE = re.compile(r'<annotation type="normalizedLabel">(.*?)</annotation>', re.S)
GREEK_ALIASES = dict(zip("αβγδεζηθικλμνξοπρστυφχψω", ("\\" + name for name in
    "alpha beta gamma delta varepsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma tau upsilon varphi chi psi omega".split())))
GREEK_ALIASES.update(dict(zip("ΓΔΘΛΞΠΣΥΦΨΩ", ("\\" + name for name in
    "Gamma Delta Theta Lambda Xi Pi Sigma Upsilon Phi Psi Omega".split()))))
GREEK_ALIASES.update({"ϵ": r"\epsilon", "ϑ": r"\vartheta", "ϰ": r"\varkappa", "ϖ": r"\varpi",
                      "ϱ": r"\varrho", "ς": r"\varsigma", "ϕ": r"\phi"})
ALIASES.update(GREEK_ALIASES)
TEXT_START_RE = re.compile(r"\\text\s*\{")
TEXT_TOKEN_RE = re.compile(r"\\[a-zA-Z]+|\\.|\s+|\S", re.S)
FUNCTIONS = tuple(sorted(("sin", "cos", "tan", "log", "ln", "lim",
                         "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh",
                         "exp", "max", "min", "cot", "sec", "csc"), key=lambda x: (-len(x), x)))
PROTECTED_WORDS = frozenset("since cost costs sinc sinhc coshc arcsinh arccosh arctanh "
                            "arccot arcsec arccsc logit logistic logarithm colim limsup liminf "
                            "limit limits minimum maximum minimal maximal maxwell second secant "
                            "cotangent distance constant".split())
WORD_RE = re.compile(r"[A-Za-z]+")
LEXEME_RE = re.compile(r"\\[a-zA-Z]+|\\.|[A-Za-z]+|\s+|.", re.S)
SPACED_WORD_RE = re.compile(r"(?<![A-Za-z\\])[A-Za-z](?:\s+[A-Za-z])+(?![A-Za-z])")
WRAPPER_RE = re.compile(r"\\(?:operatorname|mathrm|mathop)\s*\{\s*([A-Za-z\s]+)\s*\}")
PROTECTED_GROUP_RE = re.compile(r"\\(?:operatorname|mathrm|mathit|mathbf|mathsf|mathtt|begin|end)\*?\s*\{")


def _word_tokens(word: str) -> list[str]:
    if word.lower() in PROTECTED_WORDS or len(word) > 64:
        return list(word)

    def parse(rest: str):
        for offset in range(min(2, len(rest)) + 1):
            prefix = rest[:offset]
            if any(c not in "abceijkmnprstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ" for c in prefix):
                continue
            for name in FUNCTIONS:
                if not rest[offset:].startswith(name):
                    continue
                tail = rest[offset + len(name):]
                result = list(prefix) + ["\\" + name]
                if not tail or len(tail) == 1 or re.fullmatch(r"[xyzt]?d[xyzt]", tail):
                    return result + list(tail)
                following = parse(tail)
                if following is not None:
                    return result + following
        return None
    return parse(word) or list(word)


def _plain_math(text: str) -> list[str]:
    text = WRAPPER_RE.sub(lambda m: "\\" + "".join(m[1].split()) + " "
                          if "".join(m[1].split()) in FUNCTIONS else m[0], text)
    text = SPACED_WORD_RE.sub(lambda m: " ".join(_word_tokens("".join(m[0].split()))), text)
    result = []
    for match in LEXEME_RE.finditer(text):
        token = match[0]
        if token.isspace() or token in {r"\left", r"\right"}:
            continue
        if WORD_RE.fullmatch(token):
            result.extend(_word_tokens(token))
        else:
            result.append(ALIASES.get(token, token))
    return result


def math_tokens(text: str) -> list[str]:
    result, position = [], 0
    while match := PROTECTED_GROUP_RE.search(text, position):
        result.extend(_plain_math(text[position:match.start()]))
        depth, end = 1, match.end()
        for item in TOKEN_RE.finditer(text, end):
            depth += (item[0] == "{") - (item[0] == "}")
            end = item.end()
            if depth == 0:
                break
        wrapped = text[match.start():end]
        exact = WRAPPER_RE.fullmatch(wrapped)
        if exact and "".join(exact[1].split()) in FUNCTIONS:
            result.append("\\" + "".join(exact[1].split()))
        else:
            result.extend(TOKEN_RE.findall(wrapped))
        position = end
    result.extend(_plain_math(text[position:]))
    return result


def tokenize_label(label: str) -> list[str]:
    result, position = [], 0
    while match := TEXT_START_RE.search(label, position):
        result.extend(math_tokens(label[position:match.start()]))
        result.extend([r"\text", "{"])
        depth = 1
        for token_match in TEXT_TOKEN_RE.finditer(label, match.end()):
            token = token_match.group()
            if token == "{":
                depth += 1
            elif token == "}":
                depth -= 1
            result.append(" " if token.isspace() else token)
            if depth == 0:
                position = token_match.end()
                break
        else:
            raise ValueError(f"标签的 \\text 区域缺少右花括号: {label}")
    result.extend(math_tokens(label[position:]))
    return result
