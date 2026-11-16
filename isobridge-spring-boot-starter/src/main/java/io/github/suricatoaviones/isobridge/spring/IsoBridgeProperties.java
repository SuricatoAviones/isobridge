package io.github.suricatoaviones.isobridge.spring;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration of the IsoBridge auto-configuration, bound from {@code isobridge.*}. */
@ConfigurationProperties("isobridge")
public class IsoBridgeProperties {

  /** Classpath locations of the profiles to load, such as {@code classpath:profiles/pacs.yaml}. */
  private List<String> profiles = new ArrayList<>();

  /** Whether a conversion with losses fails instead of returning its report. */
  private boolean strict;

  /** Whether the XML is validated against its XSD, in both directions. */
  private boolean validateXsd = true;

  /** Whether the report entries carry their masked values. */
  private boolean reportValues;

  /** Whether the auto-configuration is enabled. */
  private boolean enabled = true;

  /** Returns the classpath locations of the profiles. */
  public List<String> getProfiles() {
    return profiles;
  }

  /** Sets the classpath locations of the profiles. */
  public void setProfiles(List<String> profiles) {
    this.profiles = profiles == null ? new ArrayList<>() : new ArrayList<>(profiles);
  }

  /** Returns whether a conversion with losses fails. */
  public boolean isStrict() {
    return strict;
  }

  /** Sets whether a conversion with losses fails. */
  public void setStrict(boolean strict) {
    this.strict = strict;
  }

  /** Returns whether the XML is validated against its XSD. */
  public boolean isValidateXsd() {
    return validateXsd;
  }

  /** Sets whether the XML is validated against its XSD. */
  public void setValidateXsd(boolean validateXsd) {
    this.validateXsd = validateXsd;
  }

  /** Returns whether the report entries carry their masked values. */
  public boolean isReportValues() {
    return reportValues;
  }

  /** Sets whether the report entries carry their masked values. */
  public void setReportValues(boolean reportValues) {
    this.reportValues = reportValues;
  }

  /** Returns whether the auto-configuration is enabled. */
  public boolean isEnabled() {
    return enabled;
  }

  /** Sets whether the auto-configuration is enabled. */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
