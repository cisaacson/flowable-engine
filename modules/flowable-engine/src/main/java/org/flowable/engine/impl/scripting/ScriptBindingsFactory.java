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

package org.flowable.engine.impl.scripting;

import java.util.ArrayList;
import java.util.List;

import javax.script.Bindings;

import org.flowable.engine.delegate.VariableScope;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;

/**
 * @author Tom Baeyens
 * @author Joram Barrez
 */
public class ScriptBindingsFactory {

    protected ProcessEngineConfigurationImpl processEngineConfiguration;
    protected List<ResolverFactory> resolverFactories;

    public ScriptBindingsFactory(ProcessEngineConfigurationImpl processEngineConfiguration, List<ResolverFactory> resolverFactories) {
        this.processEngineConfiguration = processEngineConfiguration;
        this.resolverFactories = resolverFactories;
    }

    public Bindings createBindings(VariableScope variableScope) {
        return new ScriptBindings(createResolvers(variableScope), variableScope);
    }

    public Bindings createBindings(VariableScope variableScope, boolean storeScriptVariables) {
        return new ScriptBindings(createResolvers(variableScope), variableScope, storeScriptVariables);
    }

    protected List<Resolver> createResolvers(VariableScope variableScope) {
        List<Resolver> scriptResolvers = new ArrayList<>();
        for (ResolverFactory scriptResolverFactory : resolverFactories) {
            Resolver resolver = scriptResolverFactory.createResolver(processEngineConfiguration, variableScope);
            if (resolver != null) {
                scriptResolvers.add(resolver);
            }
        }
        return scriptResolvers;
    }

    public List<ResolverFactory> getResolverFactories() {
        return resolverFactories;
    }

    public void setResolverFactories(List<ResolverFactory> resolverFactories) {
        this.resolverFactories = resolverFactories;
    }
}
