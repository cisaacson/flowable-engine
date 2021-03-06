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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.flowable.engine.common.api.FlowableObjectNotFoundException;
import org.flowable.engine.history.HistoricTaskInstance;
import org.flowable.engine.task.Attachment;
import org.flowable.engine.task.Comment;
import org.flowable.engine.task.Task;
import org.flowable.rest.service.api.engine.AttachmentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
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
public class TaskAttachmentResource extends TaskBaseResource {

    @ApiOperation(value = "Get an attachment on a task", tags = { "Tasks" })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Indicates the task and attachment were found and the attachment is returned."),
            @ApiResponse(code = 404, message = "Indicates the requested task was not found or the tasks doesn’t have a attachment with the given ID.")
    })
    @RequestMapping(value = "/runtime/tasks/{taskId}/attachments/{attachmentId}", method = RequestMethod.GET, produces = "application/json")
    public AttachmentResponse getAttachment(@ApiParam(name = "taskId") @PathVariable("taskId") String taskId, @ApiParam(name = "attachmentId") @PathVariable("attachmentId") String attachmentId, HttpServletRequest request) {

        HistoricTaskInstance task = getHistoricTaskFromRequest(taskId);

        Attachment attachment = taskService.getAttachment(attachmentId);
        if (attachment == null || !task.getId().equals(attachment.getTaskId())) {
            throw new FlowableObjectNotFoundException("Task '" + task.getId() + "' doesn't have an attachment with id '" + attachmentId + "'.", Comment.class);
        }

        return restResponseFactory.createAttachmentResponse(attachment);
    }

    @ApiOperation(value = "Delete an attachment on a task", tags = { "Tasks" })
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Indicates the task and attachment were found and the attachment is deleted. Response body is left empty intentionally."),
            @ApiResponse(code = 404, message = "Indicates the requested task was not found or the tasks doesn’t have a attachment with the given ID.")
    })
    @RequestMapping(value = "/runtime/tasks/{taskId}/attachments/{attachmentId}", method = RequestMethod.DELETE)
    public void deleteAttachment(@ApiParam(name = "taskId") @PathVariable("taskId") String taskId, @ApiParam(name = "attachmentId") @PathVariable("attachmentId") String attachmentId, HttpServletResponse response) {

        Task task = getTaskFromRequest(taskId);

        Attachment attachment = taskService.getAttachment(attachmentId);
        if (attachment == null || !task.getId().equals(attachment.getTaskId())) {
            throw new FlowableObjectNotFoundException("Task '" + task.getId() + "' doesn't have an attachment with id '" + attachmentId + "'.", Comment.class);
        }

        taskService.deleteAttachment(attachmentId);
        response.setStatus(HttpStatus.NO_CONTENT.value());
    }
}
