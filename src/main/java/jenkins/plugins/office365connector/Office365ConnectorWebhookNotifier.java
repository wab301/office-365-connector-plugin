/*
 * Copyright 2016 srhebbar.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package jenkins.plugins.office365connector;

import java.io.IOException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import java.util.logging.Logger;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.LogTaskListener;
import jenkins.plugins.office365connector.model.Card;
import jenkins.plugins.office365connector.model.Fact;
import jenkins.plugins.office365connector.model.CustomMessage;
import jenkins.plugins.office365connector.workflow.StepParameters;
import org.apache.commons.lang.StringUtils;

/**
 * @author srhebbar
 */
public final class Office365ConnectorWebhookNotifier {
    private static final Logger logger = Logger.getLogger(Webhook.class.getName());

    private static final Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
            .setPrettyPrinting().create();

    private final CardBuilder cardBuilder;
    private final DecisionMaker decisionMaker;

    private final Run run;
    private final Job job;
    private final TaskListener listener;

    public Office365ConnectorWebhookNotifier(Run run, TaskListener listener) {
        this.run = run;
        this.listener = listener;
        this.cardBuilder = new CardBuilder(run);
        this.decisionMaker = new DecisionMaker(run, listener);
        this.job = this.run.getParent();
    }

    public void sendBuildStartedNotification(boolean isFromPreBuild) {
        List<Webhook> webhooks = extractWebhooks(job);
        if (webhooks.isEmpty()) {
            return;
        }

        boolean isBuild = run instanceof AbstractBuild;
        if ((isBuild && isFromPreBuild) || (!isBuild && !isFromPreBuild)) {
            EnvVars envVars = null;
            try {
                envVars = run.getEnvironment(new LogTaskListener(logger, INFO));
            } catch (IOException e) {
                logger.log(SEVERE, e.getMessage(), e);
            } catch (InterruptedException e) {
                logger.log(SEVERE, e.getMessage(), e);
            }

            for (Webhook webhook : webhooks) {
                if (decisionMaker.isAtLeastOneRuleMatched(webhook)) {
                    if (webhook.isStartNotification()) {
                        Card card = cardBuilder.createStartedCard(getCustomMessages(webhook, envVars));
                        executeWorker(webhook, card);
                    }
                }
            }
        }
    }

    public void sendBuildCompletedNotification() {
        List<Webhook> webhooks = extractWebhooks(job);
        if (webhooks.isEmpty()) {
            return;
        }

        EnvVars envVars = null;
        try {
            envVars = run.getEnvironment(new LogTaskListener(logger, INFO));
        } catch (IOException e) {
            logger.log(SEVERE, e.getMessage(), e);
        } catch (InterruptedException e) {
            logger.log(SEVERE, e.getMessage(), e);
        }

        for (Webhook webhook : webhooks) {
            if (decisionMaker.isStatusMatched(webhook) && decisionMaker.isAtLeastOneRuleMatched(webhook)) {
                Card card = cardBuilder.createCompletedCard(getCustomMessages(webhook, envVars)); 
                executeWorker(webhook, card);
            }
        }
    }

    public static List<Fact> getCustomMessages(Webhook webhook, EnvVars envVars) {
        List<Fact> facts = new ArrayList<>();
        for (CustomMessage customMessage : webhook.getCustomMessages()) {
            logger.log(SEVERE,customMessage.getName()+customMessage.getValue());
            facts.add(new Fact(customMessage.getName(), envVars.expand(customMessage.getValue())));
        }
        
        return facts;
    }

    private static List<Webhook> extractWebhooks(Job job) {
        WebhookJobProperty property = (WebhookJobProperty) job.getProperty(WebhookJobProperty.class);
        if (property != null && property.getWebhooks() != null) {
            return property.getWebhooks();
        }
        return Collections.emptyList();
    }

    public void sendBuildNotification(StepParameters stepParameters) {
        Card card;
        if (StringUtils.isNotBlank(stepParameters.getMessage())) {
            card = cardBuilder.createBuildMessageCard(stepParameters);
        } else if (StringUtils.equalsIgnoreCase(stepParameters.getStatus(), "started")) {
            card = cardBuilder.createStartedCard();
        } else {
            card = cardBuilder.createCompletedCard();
        }

        WebhookJobProperty property = (WebhookJobProperty) job.getProperty(WebhookJobProperty.class);
        if (property == null) {
            Webhook webhook = new Webhook(stepParameters.getWebhookUrl());
            executeWorker(webhook, card);
            return;
        }

        for (Webhook webhook : property.getWebhooks()) {
            executeWorker(webhook, card);
        }
    }

    private void executeWorker(Webhook webhook, Card card) {
        try {
            HttpWorker worker = new HttpWorker(run.getEnvironment(listener).expand(webhook.getUrl()), gson.toJson(card),
                    webhook.getTimeout(), listener.getLogger());
            worker.submit();
        } catch (IOException | InterruptedException | RejectedExecutionException e) {
            log(String.format("Failed to notify webhook: %s", webhook.getName()));
            e.printStackTrace(listener.getLogger());
        }
    }

    /**
     * Helper method for logging.
     */
    private void log(String message) {
        listener.getLogger().println("[Office365connector] " + message);
    }
}
