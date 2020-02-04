package fi.vm.yti.terminology.api.model.integration;

import java.util.Objects;

import fi.vm.yti.terminology.api.model.termed.Attribute;

public class LocalizedString {

    private String value;
    private String lang;

    private LocalizedString() {
        this("", "");
    }

    public LocalizedString(final String value,
                           final String lang) {
        this.value = value;
        this.lang = lang;
    }

    public LocalizedString(final Attribute attribute) {
        value = attribute.getValue();
        lang = attribute.getLang();
    }

    public String getValue() {
        return value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(final String lang) {
        this.lang = lang;
    }

    /**
     * Convert a {@link fi.vm.yti.terminology.api.model.integration.LocalizedString} to an {@link fi.vm.yti.terminology.api.model.termed.Attribute}.
     *
     * @param maybeNull LocalizedString or null
     * @return Attribute or null, null only if the argument was null
     */
    public static Attribute asAttribute(LocalizedString maybeNull) {
        if (maybeNull != null) {
            return new Attribute(maybeNull.getLang(), maybeNull.getValue());
        }
        return null;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final LocalizedString that = (LocalizedString) o;
        return Objects.equals(value, that.value) &&
            Objects.equals(lang, that.lang);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, lang);
    }
}
