/*
 * Copyright (c) 2008-2025, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.internal.util;

import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for Strings.
 */
public final class StringUtil {

    /**
     * LOCALE_INTERNAL is the default locale for string operations and number formatting. Initialized to
     * {@code java.util.Locale.ROOT} (language neutral).
     */
    public static final Locale LOCALE_INTERNAL = Locale.ROOT;

    /**
     * Pattern used to tokenize version strings.
     */
    public static final Pattern VERSION_PATTERN
            = Pattern.compile("^(\\d+)\\.(\\d+)(\\.(\\d+))?(-\\w+(?:-\\d+)?)?(-SNAPSHOT)?$");

    private StringUtil() {
    }

    /**
     * Creates a byte array from a string.
     *
     * @param s the string.
     * @return the byte array created from the string.
     */
    public static byte[] stringToBytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Checks if a string is empty or not.
     *
     * @param s the string to check.
     * @return true if the string is {@code null} or empty, false otherwise
     */

    public static boolean isNullOrEmpty(String s) {
        if (s == null) {
            return true;
        }
        return s.isEmpty();
    }

    /**
     * Checks if a string is empty or not after trim operation
     *
     * @param s the string to check.
     * @return true if the string is {@code null} or empty, false otherwise
     */
    public static boolean isNullOrEmptyAfterTrim(String s) {
        if (s == null) {
            return true;
        }
        return s.isBlank();
    }

    /**
     * Check if all Strings are not blank
     * @param values the strings to check
     * @return true if all the strings are not {@code null} and not blank, false otherwise
     */
    public static boolean isAllNullOrEmptyAfterTrim(String... values) {
        if (values == null) {
            return false;
        }
        return Arrays.stream(values).noneMatch(StringUtil::isNullOrEmptyAfterTrim);
    }

    /**
     * Check if any String from the provided Strings
     * @param values the strings to check
     * @return true if at least one string of the {@param values} are not {@code null} and not blank
     */
    public static boolean isAnyNullOrEmptyAfterTrim(String... values) {
        if (values == null) {
            return false;
        }
        return Arrays.stream(values).anyMatch(s -> !isNullOrEmptyAfterTrim(s));
    }

    /**
     * HC specific settings, operands etc. use this method.
     * Creates an uppercase string from the given string.
     *
     * @param s the given string
     * @return an uppercase string, or {@code null}/empty if the string is {@code null}/empty
     */
    public static String upperCaseInternal(String s) {
        if (isNullOrEmpty(s)) {
            return s;
        }
        return s.toUpperCase(LOCALE_INTERNAL);
    }

    /**
     * HC specific settings, operands etc. use this method.
     * Creates a lowercase string from the given string.
     *
     * @param s the given string
     * @return a lowercase string, or {@code null}/empty if the string is {@code null}/empty
     */
    public static String lowerCaseInternal(String s) {
        if (isNullOrEmpty(s)) {
            return s;
        }
        return s.toLowerCase(LOCALE_INTERNAL);
    }

    /**
     * Returns a String representation of the time.
     * <p>
     * This method is not particularly efficient since it generates a ton of litter.
     *
     * @param timeMillis time in millis
     * @return the String
     */
    public static String timeToString(long timeMillis) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        return dateFormat.format(new Date(timeMillis));
    }

    /**
     * Returns a String representation of the time. If time is 0, then 'never' is returned.
     * <p>
     * This method is not particularly efficient since it generates a ton of litter.
     *
     * @param timeMillis time in millis
     * @return the String
     */
    public static String timeToStringFriendly(long timeMillis) {
        return timeMillis == 0 ? "never" : timeToString(timeMillis);
    }

    /**
     * Like a String.indexOf but without MIN_SUPPLEMENTARY_CODE_POINT handling
     *
     * @param input  to check the indexOf on
     * @param ch     character to find the index of
     * @param offset offset to start the reading from
     * @return index of the character, or -1 if not found
     */
    public static int indexOf(String input, char ch, int offset) {
        for (int i = offset; i < input.length(); i++) {
            if (input.charAt(i) == ch) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Like a String.indexOf but without MIN_SUPPLEMENTARY_CODE_POINT handling
     *
     * @param input to check the indexOf on
     * @param ch    character to find the index of
     * @return index of the character, or -1 if not found
     */
    public static int indexOf(String input, char ch) {
        return indexOf(input, ch, 0);
    }

    /**
     * Like a String.lastIndexOf but without MIN_SUPPLEMENTARY_CODE_POINT handling
     *
     * @param input  to check the indexOf on
     * @param ch     character to find the index of
     * @param offset offset to start the reading from the end
     * @return index of the character, or -1 if not found
     */
    public static int lastIndexOf(String input, char ch, int offset) {
        for (int i = input.length() - 1 - offset; i >= 0; i--) {
            if (input.charAt(i) == ch) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Like a String.lastIndexOf but without MIN_SUPPLEMENTARY_CODE_POINT handling
     *
     * @param input to check the indexOf on
     * @param ch    character to find the index of
     * @return index of the character, or -1 if not found
     */
    public static int lastIndexOf(String input, char ch) {
        return lastIndexOf(input, ch, 0);
    }

    /**
     * Tokenizes a version string and returns the tokens with the following grouping:
     * (1) major version, eg "3"
     * (2) minor version, eg "8"
     * (3) patch version prefixed with ".", if exists, otherwise {@code null} (eg ".0")
     * (4) patch version, eg "0"
     * (5) 1st -qualifier, if exists
     * (6) -SNAPSHOT qualifier, if exists
     */
    public static String[] tokenizeVersionString(String version) {
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (matcher.matches()) {
            String[] tokens = new String[matcher.groupCount()];
            for (int i = 0; i < matcher.groupCount(); i++) {
                tokens[i] = matcher.group(i + 1);
            }
            return tokens;
        } else {
            return null;
        }
    }


    /**
     * Trim whitespaces using the more aggressive approach of {@link String#strip()}.
     * This method removes leading and trailing whitespaces, including a broader set of Unicode whitespace characters,
     * compared to {@link String#trim()}.
     *
     * @param input string to trim
     * @return {@code null} if provided value was {@code null}, input with removed leading and trailing whitespaces
     */
    public static String trim(String input) {
        if (input == null) {
            return null;
        }
        return input.strip();
    }

    /**
     * Splits String value with comma "," used as a separator. The whitespaces around values are trimmed.
     *
     * @param input string to split
     * @return {@code null} if provided value was {@code null}, split parts otherwise (trimmed)
     */
    public static String[] splitByComma(String input, boolean allowEmpty) {
        if (input == null) {
            return null;
        }
        String[] splitWithEmptyValues = trim(input).split("\\s*,\\s*", -1);
        return allowEmpty ? splitWithEmptyValues : subtraction(splitWithEmptyValues, new String[]{""});
    }

    /**
     * Returns intersection of given String arrays. If either array is {@code null}, then {@code null} is returned.
     *
     * @param arr1 first array
     * @param arr2 second array
     * @return arr1 without values which are not present in arr2
     */
    public static String[] intersection(String[] arr1, String[] arr2) {
        if (arr1 == null || arr2 == null) {
            return null;
        }
        if (arr1.length == 0 || arr2.length == 0) {
            return new String[0];
        }
        List<String> list = new ArrayList<>(Arrays.asList(arr1));
        list.retainAll(Arrays.asList(arr2));
        return list.toArray(new String[0]);
    }

    /**
     * Returns subtraction between given String arrays.
     *
     * @param arr1 first array
     * @param arr2 second array
     * @return arr1 without values which are not present in arr2
     */
    public static String[] subtraction(String[] arr1, String[] arr2) {
        if (arr1 == null || arr1.length == 0 || arr2 == null || arr2.length == 0) {
            return arr1;
        }
        List<String> list = new ArrayList<>(Arrays.asList(arr1));
        list.removeAll(Arrays.asList(arr2));
        return list.toArray(new String[0]);
    }

    /**
     * @param str1 first string to compare
     * @param str2 second string to compare
     * @return {@code true} if the two strings are equals ignoring the letter case in {@link #LOCALE_INTERNAL} locale.
     */
    @SuppressWarnings("java:S4973")
    public static boolean equalsIgnoreCase(String str1, String str2) {
        return (str1 != null && str2 != null) && (str1 == str2 || lowerCaseInternal(str1).equals(lowerCaseInternal(str2)));
    }

    /**
     * Strips the trailing slash from the input string, if it is present
     *
     * @return the string with trailing slash removed
     */
    public static String stripTrailingSlash(String str) {
        if (isNullOrEmpty(str)) {
            return str;
        }
        if (str.charAt(str.length() - 1) == '/') {
            return str.substring(0, str.length() - 1);
        }
        return str;
    }

    /**
     * Returns a string where named placeholders are replaced by values from the
     * given {@code variableValues} map. The placeholder is defined as the
     * variable name prefixed by ${@code placeholderNamespace}&#123; and followed
     * by &#125;. For example, if the {@code placeholderNamespace} is {@code HZ_TEST}
     * the placeholder for "instance_name" would be $HZ_TEST&#123;instance_name&#125;.
     * <p>
     * The variable replacement is fail-safe which means any incorrect syntax such
     * as missing closing brackets or missing variable values is ignored.
     *
     * @param pattern              the pattern in which placeholders should be replaced
     * @param placeholderNamespace the string inserted into the placeholder prefix to distinguish between
     *                             different types of placeholders
     * @param variableValues       the placeholder variable values
     * @return the formatted string
     */
    public static String resolvePlaceholders(String pattern,
                                             String placeholderNamespace,
                                             Map<String, Object> variableValues) {
        StringBuilder sb = new StringBuilder(pattern);
        String placeholderPrefix = "$" + placeholderNamespace + "{";
        int endIndex;
        int startIndex = sb.indexOf(placeholderPrefix);

        while (startIndex > -1) {
            endIndex = sb.indexOf("}", startIndex);
            if (endIndex == -1) {
                // ignore bad syntax, search finished
                break;
            }

            String variableName = sb.substring(startIndex + placeholderPrefix.length(), endIndex);
            Object variableValue = variableValues.get(variableName);
            // ignore missing values
            if (variableValue != null) {
                String valueStr = variableValue.toString();
                sb.replace(startIndex, endIndex + 1, valueStr);
                endIndex = startIndex + valueStr.length();
            }

            startIndex = sb.indexOf(placeholderPrefix, endIndex);
        }
        return sb.toString();
    }

    /**
     * Converts the provided collection to string, joined by LINE_SEPARATOR
     * @param collection collection to convert to string
     * @return string
     */
    public static <T> String toString(Collection<T> collection) {
        return collection.stream()
                .map(Objects::toString)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    /**
     * Converts the provided array to string, joined by LINE_SEPARATOR
     * @param arr array to convert to string
     * @return string
     */
    public static <T> String toString(T[] arr) {
        return Arrays.stream(arr)
                .map(Objects::toString)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    /**
     * Ensures that the returned string is at most {@code maxLength} long. If
     * it's longer, trims it to one char less (not taking word boundaries into
     * account), and appends an ellipsis. Returns {@code null} for null input.
     *
     * @param s The string to shorten
     * @param maxLength Maximum length the returned string must have
     * @return Shortened string
     */
    public static String shorten(String s, int maxLength) {
        Preconditions.checkPositive("maxLength", maxLength);
        if (s == null || s.length() <= maxLength) {
            return s;
        }
        return s.substring(maxLength - 1) + '…';
    }

    /**
     * Removes all occurrence of {@code charToRemove} from {@code str}. This method is more efficient than
     * {@link String#replaceAll(String, String)} which compiles a regex from the first parameter every invocation.
     */
    public static String removeCharacter(String str, char charToRemove) {
        if (str == null || str.indexOf(charToRemove) == -1) {
            return str;
        }
        char[] chars = str.toCharArray();
        int pos = 0;
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] != charToRemove) {
                chars[pos++] = chars[i];
            }
        }
        return new String(chars, 0, pos);
    }

    public static boolean isBoolean(String value) {
        return value.equalsIgnoreCase("false") || value.equalsIgnoreCase("true");
    }
}
