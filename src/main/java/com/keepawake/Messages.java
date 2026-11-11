package com.keepawake;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * UI text from {@code messages*.properties}, in the system language (English when there is no translation). Every
 * message is a MessageFormat pattern, so a literal apostrophe is written {@code ''}.
 */
final class Messages {
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("com.keepawake.messages");

    private Messages() {}

    static String get(String key) {
        return format(key);
    }

    /** The message with {@code {0}}, {@code {1}}… replaced by {@code args}. */
    static String format(String key, Object... args) {
        return new MessageFormat(BUNDLE.getString(key), BUNDLE.getLocale()).format(args);
    }
}
