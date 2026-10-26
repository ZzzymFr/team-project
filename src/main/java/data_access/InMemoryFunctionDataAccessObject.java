package data_access;

import entity.UserFunction;
import use_case.evaluate.FunctionDataAccessInterface;

import java.util.HashMap;
import java.util.Map;

public class InMemoryFunctionDataAccessObject implements FunctionDataAccessInterface {
    private final Map<String, UserFunction> functions = new HashMap<>();

    @Override
    public void saveFunction(UserFunction function) {
        functions.put(function.getName(), function);
    }

    @Override
    public Map<String, UserFunction> getAllFunctions() {
        return new HashMap<>(functions);
    }
}
