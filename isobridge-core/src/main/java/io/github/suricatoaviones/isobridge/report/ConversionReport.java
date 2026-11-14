package io.github.suricatoaviones.isobridge.report;

import io.github.suricatoaviones.isobridge.ConversionDirection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * What happened during one conversion: which fields were mapped, defaulted, generated, inferred,
 * carried as supplementary data, dropped or excluded.
 *
 * <p>{@code lossless} is true only when no entry is {@link Status#DROPPED}; an {@link
 * Status#EXCLUDED} field is a decision of the profile and does not count as a loss. The record is
 * immutable, copies its entries and is safe to share.
 *
 * @param profile the identifier of the profile used
 * @param profileVersion the version of the profile
 * @param direction which way the conversion went
 * @param lossless whether every source field reached the target
 * @param entries the entries, in profile order and then by field number
 */
public record ConversionReport(
    String profile,
    int profileVersion,
    ConversionDirection direction,
    boolean lossless,
    List<ReportEntry> entries) {

  private static final List<Status> SUMMARY_ORDER =
      List.of(
          Status.MAPPED,
          Status.DEFAULTED,
          Status.GENERATED,
          Status.SUPPLEMENTARY,
          Status.INFERRED,
          Status.DROPPED,
          Status.EXCLUDED);

  public ConversionReport {
    Objects.requireNonNull(profile, "profile");
    if (profile.isBlank()) {
      throw new IllegalArgumentException("The profile of a report must not be blank");
    }
    if (profileVersion < 1) {
      throw new IllegalArgumentException(
          "The profile version of a report must be at least 1: " + profileVersion);
    }
    Objects.requireNonNull(direction, "direction");
    entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    boolean expected = entries.stream().noneMatch(entry -> entry.status() == Status.DROPPED);
    if (lossless != expected) {
      throw new IllegalArgumentException(
          "lossless must be " + expected + " for these entries, but was " + lossless);
    }
  }

  /**
   * Builds a report and computes {@code lossless} from its entries.
   *
   * @param profile the identifier of the profile used
   * @param profileVersion the version of the profile
   * @param direction which way the conversion went
   * @param entries the entries, in profile order and then by field number
   * @return the report
   */
  public static ConversionReport of(
      String profile,
      int profileVersion,
      ConversionDirection direction,
      List<ReportEntry> entries) {
    Objects.requireNonNull(entries, "entries");
    boolean lossless = entries.stream().noneMatch(entry -> entry.status() == Status.DROPPED);
    return new ConversionReport(profile, profileVersion, direction, lossless, entries);
  }

  /** Whether at least one field was dropped. */
  public boolean hasLosses() {
    return !lossless;
  }

  /** The entries whose status is {@link Status#DROPPED}, in report order. */
  public List<ReportEntry> dropped() {
    return entries.stream().filter(entry -> entry.status() == Status.DROPPED).toList();
  }

  /**
   * Returns one line suitable for a log, without any field value.
   *
   * <p>Example: {@code card-transfer-pacs008 v1 ISO8583_TO_ISO20022: 12 mapped, 2 supplementary, 1
   * inferred, 1 dropped (de55), 2 excluded}. Only the statuses that appear are listed, and the
   * dropped ones carry their sources. A report without entries ends with {@code no entries}.
   *
   * @return the summary line
   */
  public String summary() {
    if (entries.isEmpty()) {
      return profile + " v" + profileVersion + " " + direction + ": no entries";
    }
    Map<Status, Integer> counts = new EnumMap<>(Status.class);
    for (ReportEntry entry : entries) {
      counts.merge(entry.status(), 1, Integer::sum);
    }
    StringJoiner parts = new StringJoiner(", ");
    for (Status status : SUMMARY_ORDER) {
      int count = counts.getOrDefault(status, 0);
      if (count == 0) {
        continue;
      }
      String part = count + " " + status.label();
      if (status == Status.DROPPED) {
        part += " (" + droppedSources() + ")";
      }
      parts.add(part);
    }
    return profile + " v" + profileVersion + " " + direction + ": " + parts;
  }

  private String droppedSources() {
    StringJoiner sources = new StringJoiner(", ");
    for (ReportEntry entry : entries) {
      if (entry.status() == Status.DROPPED) {
        sources.add(entry.source());
      }
    }
    return sources.toString();
  }
}
