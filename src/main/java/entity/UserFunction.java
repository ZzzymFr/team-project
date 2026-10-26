package entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UserFunction {
    private final String name;
    private final String parameter;
    private final List<String> body;
    private final String bodyText;

    public UserFunction(String name, String parameter, List<String> body, String bodyText) {
        this.name = name;
        this.parameter = parameter;
        this.body = new ArrayList<>(body);
        this.bodyText = bodyText;
    }

    public String getName() {
        return name;
    }

    public String getParameter() {
        return parameter;
    }

    public List<String> getBody() {
        return Collections.unmodifiableList(body);
    }

    public String getBodyText() {
        return bodyText;
    }
}
