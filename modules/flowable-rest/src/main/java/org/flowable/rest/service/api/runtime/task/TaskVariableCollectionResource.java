/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.flowable.rest.service.api.runtime.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.flowable.engine.common.api.FlowableIllegalArgumentException;
import org.flowable.engine.task.Task;
import org.flowable.rest.exception.FlowableConflictException;
import org.flowable.rest.service.api.RestResponseFactory;
import org.flowable.rest.service.api.engine.variable.RestVariable;
import org.flowable.rest.service.api.engine.variable.RestVariable.RestVariableScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

/**
 * @author Frederik Heremans
 */
@RestController
@Api(tags = { "Tasks" }, description = "Manage Tasks", authorizations = { @Authorization(value = "basicAuth") })
public class TaskVariableCollectionResource extends TaskVariableBaseResource {

    @Autowired
    protected ObjectMapper objectMapper;

    @ApiOperation(value = "Get all variables for a task", tags = { "Tasks" }, nickname = "listTaskVariables")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Indicates the task was found and the requested variables are returned"),
            @ApiResponse(code = 404, message = "Indicates the requested task was not found..")
    })
    @ApiImplicitParams(@ApiImplicitParam(name = "scope", dataType = "string", value = "Scope of variable to be returned. When local, only task-local variable value is returned. When global, only variable value from the task’s parent execution-hierarchy are returned. When the parameter is omitted, a local variable will be returned if it exists, otherwise a global variable.", paramType = "query"))
    @RequestMapping(value = "/runtime/tasks/{taskId}/variables", method = RequestMethod.GET, produces = "application/json")
    public List<RestVariable> getVariables(@ApiParam(name = "taskId") @PathVariable String taskId, @ApiParam(hidden = true) @RequestParam(value = "scope", required = false) String scope, HttpServletRequest request) {

        List<RestVariable> result = new ArrayList<>();
        Map<String, RestVariable> variableMap = new HashMap<>();

        // Check if it's a valid task to get the variables for
        Task task = getTaskFromRequest(taskId);

        RestVariableScope variableScope = RestVariable.getScopeFromString(scope);
        if (variableScope == null) {
            // Use both local and global variables
            addLocalVariables(task, variableMap);
            addGlobalVariables(task, variableMap);

        } else if (variableScope == RestVariableScope.GLOBAL) {
            addGlobalVariables(task, variableMap);

        } else if (variableScope == RestVariableScope.LOCAL) {
            addLocalVariables(task, variableMap);
        }

        // Get unique variables from map
        result.addAll(variableMap.values());
        return result;
    }

    // FIXME Multiple Endpoints
    @ApiOperation(value = "Create new variables on a task", tags = { "Tasks" }, notes = "## Request body for creating simple (non-binary) variables\n\n"
            + " ```JSON\n" + "[\n" + "  {\n" + "    \"name\" : \"myTaskVariable\",\n" + "    \"scope\" : \"local\",\n" + "    \"type\" : \"string\",\n"
            + "    \"value\" : \"Hello my friend\"\n" + "  },\n" + "  {\n" + "\n" + "  }\n" + "] ```"
            + "\n\n\n"
            + "The request body should be an array containing one or more JSON-objects representing the variables that should be created.\n" + "\n"
            + "- *name*: Required name of the variable\n" + "\n" + "scope: Scope of variable that is created. If omitted, local is assumed.\n" + "\n"
            + "- *type*: Type of variable that is created. If omitted, reverts to raw JSON-value type (string, boolean, integer or double).\n" + "\n"
            + "- *value*: Variable value.\n" + "\n" + "More information about the variable format can be found in the REST variables section."
            + "\n\n\n"
            + "## Request body for Creating a new binary variable\n\n"
            + "The request should be of type multipart/form-data. There should be a single file-part included with the binary value of the variable. On top of that, the following additional form-fields can be present:\n"
            + "\n"
            + "- *name*: Required name of the variable.\n" + "\n" + "scope: Scope of variable that is created. If omitted, local is assumed.\n" + "\n"
            + "- *type*: Type of variable that is created. If omitted, binary is assumed and the binary data in the request will be stored as an array of bytes."
            + "\n\n\n")
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Indicates the variables were created and the result is returned."),
            @ApiResponse(code = 400, message = "Indicates the name of a variable to create was missing or that an attempt is done to create a variable on a standalone task (without a process associated) with scope global or an empty array of variables was included in the request or request did not contain an array of variables. Status message provides additional information."),
            @ApiResponse(code = 404, message = "Indicates the requested task was not found."),
            @ApiResponse(code = 409, message = "Indicates the task already has a variable with the given name. Use the PUT method to update the task variable instead."),
            @ApiResponse(code = 415, message = "Indicates the serializable data contains an object for which no class is present in the JVM running the Flowable engine and therefore cannot be deserialized.")
    })
    @RequestMapping(value = "/runtime/tasks/{taskId}/variables", method = RequestMethod.POST, produces = "application/json")
    public Object createTaskVariable(@ApiParam(name = "taskId") @PathVariable String taskId, HttpServletRequest request, HttpServletResponse response) {

        Task task = getTaskFromRequest(taskId);

        Object result = null;
        if (request instanceof MultipartHttpServletRequest) {
            result = setBinaryVariable((MultipartHttpServletRequest) request, task, true);
        } else {

            List<RestVariable> inputVariables = new ArrayList<>();
            List<RestVariable> resultVariables = new ArrayList<>();
            result = resultVariables;

            try {
                @SuppressWarnings("unchecked")
                List<Object> variableObjects = (List<Object>) objectMapper.readValue(request.getInputStream(), List.class);
                for (Object restObject : variableObjects) {
                    RestVariable restVariable = objectMapper.convertValue(restObject, RestVariable.class);
                    inputVariables.add(restVariable);
                }
            } catch (Exception e) {
                throw new FlowableIllegalArgumentException("Failed to serialize to a RestVariable instance", e);
            }

            if (inputVariables == null || inputVariables.size() == 0) {
                throw new FlowableIllegalArgumentException("Request didn't contain a list of variables to create.");
            }

            RestVariableScope sharedScope = null;
            RestVariableScope varScope = null;
            Map<String, Object> variablesToSet = new HashMap<>();

            for (RestVariable var : inputVariables) {
                // Validate if scopes match
                varScope = var.getVariableScope();
                if (var.getName() == null) {
                    throw new FlowableIllegalArgumentException("Variable name is required");
                }

                if (varScope == null) {
                    varScope = RestVariableScope.LOCAL;
                }
                if (sharedScope == null) {
                    sharedScope = varScope;
                }
                if (varScope != sharedScope) {
                    throw new FlowableIllegalArgumentException("Only allowed to update multiple variables in the same scope.");
                }

                if (hasVariableOnScope(task, var.getName(), varScope)) {
                    throw new FlowableConflictException("Variable '" + var.getName() + "' is already present on task '" + task.getId() + "'.");
                }

                Object actualVariableValue = restResponseFactory.getVariableValue(var);
                variablesToSet.put(var.getName(), actualVariableValue);
                resultVariables.add(restResponseFactory.createRestVariable(var.getName(), actualVariableValue, varScope, task.getId(), RestResponseFactory.VARIABLE_TASK, false));
            }

            if (!variablesToSet.isEmpty()) {
                if (sharedScope == RestVariableScope.LOCAL) {
                    taskService.setVariablesLocal(task.getId(), variablesToSet);
                } else {
                    if (task.getExecutionId() != null) {
                        // Explicitly set on execution, setting non-local
                        // variables on task will override local-variables if
                        // exists
                        runtimeService.setVariables(task.getExecutionId(), variablesToSet);
                    } else {
                        // Standalone task, no global variables possible
                        throw new FlowableIllegalArgumentException("Cannot set global variables on task '" + task.getId() + "', task is not part of process.");
                    }
                }
            }
        }

        response.setStatus(HttpStatus.CREATED.value());
        return result;
    }

    @ApiOperation(value = "Delete all local variables on a task", tags = { "Tasks" })
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Indicates all local task variables have been deleted. Response-body is intentionally empty."),
            @ApiResponse(code = 404, message = "Indicates the requested task was not found.")
    })
    @RequestMapping(value = "/runtime/tasks/{taskId}/variables", method = RequestMethod.DELETE)
    public void deleteAllLocalTaskVariables(@ApiParam(name = "taskId") @PathVariable String taskId, HttpServletResponse response) {
        Task task = getTaskFromRequest(taskId);
        Collection<String> currentVariables = taskService.getVariablesLocal(task.getId()).keySet();
        taskService.removeVariablesLocal(task.getId(), currentVariables);

        response.setStatus(HttpStatus.NO_CONTENT.value());
    }

    protected void addGlobalVariables(Task task, Map<String, RestVariable> variableMap) {
        if (task.getExecutionId() != null) {
            Map<String, Object> rawVariables = runtimeService.getVariables(task.getExecutionId());
            List<RestVariable> globalVariables = restResponseFactory.createRestVariables(rawVariables, task.getId(), RestResponseFactory.VARIABLE_TASK, RestVariableScope.GLOBAL);

            // Overlay global variables over local ones. In case they are
            // present the values are not overridden,
            // since local variables get precedence over global ones at all
            // times.
            for (RestVariable var : globalVariables) {
                if (!variableMap.containsKey(var.getName())) {
                    variableMap.put(var.getName(), var);
                }
            }
        }
    }

    protected void addLocalVariables(Task task, Map<String, RestVariable> variableMap) {
        Map<String, Object> rawVariables = taskService.getVariablesLocal(task.getId());
        List<RestVariable> localVariables = restResponseFactory.createRestVariables(rawVariables, task.getId(), RestResponseFactory.VARIABLE_TASK, RestVariableScope.LOCAL);

        for (RestVariable var : localVariables) {
            variableMap.put(var.getName(), var);
        }
    }
}
