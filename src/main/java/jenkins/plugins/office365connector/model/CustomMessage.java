package jenkins.plugins.office365connector.model;

import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Defines name and value for the custom message.
 */
public class CustomMessage extends AbstractDescribableImpl<CustomMessage> {

    private final String name;

    private final String value;

    @DataBoundConstructor
    public CustomMessage(String name, String value) {
        this.name = Util.fixNull(name);
        this.value = Util.fixNull(value);
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<CustomMessage> {

        @Nonnull
        @Override
        public String getDisplayName() {
            return "CustomMessage";
        }
    }
}