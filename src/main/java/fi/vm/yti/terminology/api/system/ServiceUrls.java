package fi.vm.yti.terminology.api.system;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;


    @ConfigurationProperties("services")
    @Component
    @Validated
    public class ServiceUrls {

        @NotNull
        private String codeListUrl;

        @NotNull
        private String dataModelUrl;

        @NotNull
        private String groupManagementUrl;

        @NotNull
        private String commentsUrl;

        @NotNull
        private String env;

        private boolean messagingEnabled;

        public String getCodeListUrl() {
            return codeListUrl;
        }

        public void setCodeListUrl(String codeListUrl) {
            this.codeListUrl = codeListUrl;
        }

        public String getDataModelUrl() {
            return dataModelUrl;
        }

        public void setDataModelUrl(String dataModelUrl) {
            this.dataModelUrl = dataModelUrl;
        }

        public String getCommentsUrl() {
            return commentsUrl;
        }

        public void setCommentsUrl(String commentsUrl) {
            this.commentsUrl = commentsUrl;
        }

        public String getGroupManagementUrl() {
            return groupManagementUrl;
        }

        public void setGroupManagementUrl(String groupManagementUrl) {
            this.groupManagementUrl = groupManagementUrl;
        }

        public String getEnv() {
            return env;
        }

        public void setEnv(String env) {
            this.env = env;
        }

        public boolean getMessagingEnabled() {
            return messagingEnabled;
        }

        public void setMessagingEnabled(final boolean messagingEnabled) {
            this.messagingEnabled = messagingEnabled;
        }
    }

