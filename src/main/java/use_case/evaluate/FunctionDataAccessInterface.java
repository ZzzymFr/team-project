package use_case.evaluate;

import entity.UserFunction;

import java.util.Map;

public interface FunctionDataAccessInterface {
    void saveFunction(UserFunction function);

    Map<String, UserFunction> getAllFunctions();
}
