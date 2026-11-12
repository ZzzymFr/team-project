package entity.symbolic;

import entity.Context;
import entity.UserFunction;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// the context's functions as a map, keeps every one that got looked up
final class SeenFunctions extends AbstractMap<String, UserFunction> {
    private final Context context;
    private final Map<String, UserFunction> seen = new HashMap<>();

    SeenFunctions(Context context) {
        this.context = context;
    }

    @Override
    public UserFunction get(Object name) {
        UserFunction function = context.getFunction((String) name);
        seen.put((String) name, function);
        return function;
    }

    @Override
    public boolean containsKey(Object name) {
        return get(name) != null;
    }

    // only the ones seen so far
    @Override
    public Set<Entry<String, UserFunction>> entrySet() {
        return Collections.unmodifiableMap(seen).entrySet();
    }

    // what was looked up, to check later nothing got redefined
    Map<String, UserFunction> snapshot() {
        return new HashMap<>(seen);
    }
}
