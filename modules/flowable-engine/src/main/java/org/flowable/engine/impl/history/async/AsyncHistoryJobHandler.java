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
package org.flowable.engine.impl.history.async;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flowable.engine.impl.history.async.json.transformer.ActivityEndHistoryJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.ActivityFullHistoryJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.ActivityStartHistoryJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.FormPropertiesSubmittedHistoryJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.HistoricDetailVariableUpdateHistoryJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.HistoryJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.IdentityLinkCreatedHistoryJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.IdentityLinkDeletedHistoryJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.ProcessInstanceDeleteHistoryByProcessDefinitionIdJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.ProcessInstanceDeleteHistoryJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.ProcessInstanceEndHistoryJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.ProcessInstancePropertyChangedHistoryJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.ProcessInstanceStartHistoryJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.SubProcessInstanceHistoryJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.TaskAssigneeChangedHistoryJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.TaskCreatedHistoryJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.TaskEndedHistoryJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.TaskPropertyChangedHistoryJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.VariableCreatedHistoryJsonTransformer;
import org.flowable.engine.impl.history.async.json.transformer.VariableUpdatedHistoryJsonTransformer;
import org.flowable.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.persistence.entity.HistoryJobEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AsyncHistoryJobHandler extends AbstractAsyncHistoryJobHandler {

    private static final Logger logger = LoggerFactory.getLogger(AsyncHistoryJobHandler.class);

    public static final String JOB_TYPE = "async-history";

    protected Map<String, List<HistoryJsonTransformer>> historyJsonTransformers = new HashMap<String, List<HistoryJsonTransformer>>();

    public AsyncHistoryJobHandler() {
        initDefaultTransformers();
    }

    @Override
    public String getType() {
        return JOB_TYPE;
    }

    protected void initDefaultTransformers() {
        addHistoryJsonTransformer(new ProcessInstanceStartHistoryJsonTransformer());
        addHistoryJsonTransformer(new ProcessInstanceEndHistoryJsonTransformer());
        addHistoryJsonTransformer(new ProcessInstanceDeleteHistoryJsonTransformer());
        addHistoryJsonTransformer(new ProcessInstanceDeleteHistoryByProcessDefinitionIdJsonTransformer());
        addHistoryJsonTransformer(new ProcessInstancePropertyChangedHistoryJsonTransformer());
        addHistoryJsonTransformer(new SubProcessInstanceHistoryJsonTransformer());

        addHistoryJsonTransformer(new ActivityStartHistoryJsonTransformer());
        addHistoryJsonTransformer(new ActivityEndHistoryJsonTransformer());
        addHistoryJsonTransformer(new ActivityFullHistoryJsonTransformer());

        addHistoryJsonTransformer(new TaskCreatedHistoryJsonTransformer());
        addHistoryJsonTransformer(new TaskEndedHistoryJsonTransformer());

        addHistoryJsonTransformer(new TaskPropertyChangedHistoryJsonTransformer());
        addHistoryJsonTransformer(new TaskAssigneeChangedHistoryJsonTransformer());
        
        addHistoryJsonTransformer(new IdentityLinkCreatedHistoryJsonTransformer());
        addHistoryJsonTransformer(new IdentityLinkDeletedHistoryJsonTransformer());
        
        addHistoryJsonTransformer(new VariableCreatedHistoryJsonTransformer());
        addHistoryJsonTransformer(new VariableUpdatedHistoryJsonTransformer());
        addHistoryJsonTransformer(new HistoricDetailVariableUpdateHistoryJsonTransformer());
        addHistoryJsonTransformer(new FormPropertiesSubmittedHistoryJsonTransformer());
    }

    public void addHistoryJsonTransformer(HistoryJsonTransformer historyJsonTransformer) {
        String type = historyJsonTransformer.getType();
        if (!historyJsonTransformers.containsKey(type)) {
            historyJsonTransformers.put(type, new ArrayList<HistoryJsonTransformer>());
        }
        historyJsonTransformers.get(historyJsonTransformer.getType()).add(historyJsonTransformer);
    }

    @Override
    protected void processHistoryJson(CommandContext commandContext, HistoryJobEntity job, JsonNode historyNode) {
        System.out.println("!!!!!!! processHistoryJson id=" + job.getId() + " " + historyNode);
        
        String type = historyNode.get("type").asText();
        ObjectNode historicalJsonData = (ObjectNode) historyNode.get("data");

        if (logger.isDebugEnabled()) {
            logger.debug("Handling async history job (id={}, type={})", job.getId(), type);
        }

        List<HistoryJsonTransformer> transformers = historyJsonTransformers.get(type);
        if (transformers != null && !transformers.isEmpty()) {
            for (HistoryJsonTransformer transformer : transformers) {
                if (transformer.isApplicable(historicalJsonData, commandContext)) {
                    transformer.transformJson(job, historicalJsonData, commandContext);

                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Could not handle history job (id={}) for transformer {}. as it is not applicable. Unacquiring. " + historicalJsonData, 
                                job.getId(), transformer.getType());
                    }
                    throw new AsyncHistoryJobNotApplicableException();

                }
            }
        } else {
            logger.debug("Cannot transform history json: no transformers found for type {}", type);
        }
    }

    public Map<String, List<HistoryJsonTransformer>> getHistoryJsonTransformers() {
        return historyJsonTransformers;
    }

    public void setHistoryJsonTransformers(Map<String, List<HistoryJsonTransformer>> historyJsonTransformers) {
        this.historyJsonTransformers = historyJsonTransformers;
    }

}
