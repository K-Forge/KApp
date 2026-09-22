package co.edu.konradlorenz.kapp.semaphore.domain;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Weekly contact hours: a whole number, or a whole number and a half.
 *
 * <h2>Why a half exists at all</h2>
 * Four items across the published plans print one. Psicología's two professional practices
 * are printed as <em>4,5</em> hours and the practices of Marketing and Negocios
 * Internacionales as <em>1,5</em>. Everything else is whole, and rounding those four up was
 * what made Psicología add up to 175 weekly hours against the 174 its own document prints.
 * A plan that disagrees with its own total is a plan nobody can check, so the half stays.
 *
 * <h2>Why a double is exact here</h2>
 * Halves are the only fraction that occurs, and a half is a power of two: 0.5, 4.5 and
 * 174.5 are all represented exactly in IEEE 754, as is every sum and every multiplication by
 * 16 at this scale. The usual objection to floating point - 0.1 + 0.2 - is about fractions
 * that are not powers of two, and {@link #isValid(double)} is what keeps one from getting
 * in: a plan may declare 4 or 4.5 and is refused if it tries to declare 4.3.
 *
 * <p>Half hours are the exception and are written as one. {@link Serializer} keeps whole
 * hours whole on the wire - {@code 4}, never {@code 4.0} - so the four practices are the only
 * place in the API where a decimal point appears, and a client that reads hours as an integer
 * keeps working everywhere else.
 */
public final class WeeklyHours {

    /** The smallest step a plan may print. Anything between two steps is a transcription error. */
    public static final double STEP = 0.5;

    private WeeklyHours() {
    }

    /** @return true when {@code hours} is zero or more and lands on a whole hour or a half */
    public static boolean isValid(double hours) {
        // Distance to the nearest step, not `hours % STEP`: the modulo of a value a hair below
        // a step is a hair below STEP itself, which would reject 4.499999999999 while accepting
        // 4.500000000001. Both are the same printed 4,5.
        return Double.isFinite(hours) && hours >= 0
                && Math.abs(hours - Math.round(hours / STEP) * STEP) < 1e-9;
    }

    /**
     * @param text a figure as a document or a spreadsheet prints it: {@code 4}, {@code 4.5}
     *             or the Spanish {@code 4,5}
     * @throws IllegalArgumentException if it is not a number, or not a whole hour or a half
     */
    public static double parse(String text) {
        String normalised = text == null ? "" : text.trim().replace(',', '.');
        double hours;
        try {
            hours = Double.parseDouble(normalised);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'%s' is not a number of hours".formatted(text));
        }
        if (!isValid(hours)) {
            throw new IllegalArgumentException(
                    "'%s' must be a whole number of hours or a half, such as 4 or 4.5".formatted(text));
        }
        return hours;
    }

    /** @return the figure as a person reads it: {@code "4"} for a whole hour, {@code "4.5"} for a half */
    public static String format(double hours) {
        return hours == Math.rint(hours)
                ? String.valueOf((long) hours)
                : String.valueOf(hours);
    }

    /** Writes a whole number of hours as an integer, so only the halves carry a decimal point. */
    public static final class Serializer extends JsonSerializer<Double> {

        @Override
        public void serialize(Double value, JsonGenerator json, SerializerProvider provider)
                throws IOException {
            if (value == null) {
                json.writeNull();
            } else if (value == Math.rint(value)) {
                json.writeNumber(value.longValue());
            } else {
                json.writeNumber(value);
            }
        }
    }
}
