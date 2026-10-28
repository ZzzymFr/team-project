package entity;

import entity.node.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UserFunction {
    private final String name;
    private final List<String> parameters;
    private final Node body;
    private final String bodyText;

    public UserFunction(String name, List<String> parameters, Node body, String bodyText) {
        this.name = name;
        this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
        this.body = body;
        this.bodyText = bodyText;
    }

    public String getName() {
        return name;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public Node getBody() {
        return body;
    }

    public String getBodyText() {
        return bodyText;
    }
}
