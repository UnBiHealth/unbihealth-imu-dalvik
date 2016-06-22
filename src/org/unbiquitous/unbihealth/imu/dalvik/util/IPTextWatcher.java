package org.unbiquitous.unbihealth.imu.dalvik.util;

import android.text.Editable;
import android.text.TextWatcher;

import java.util.regex.Pattern;

/**
 * Validates a (partial) IP address text input field.
 *
 * @author Luciano Santos
 */
public class IPTextWatcher implements TextWatcher {
    private static final Pattern PARTIAl_IP_ADDRESS = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[0-9])\\.){0,3}" +
                    "((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[0-9])){0,1}$"
    );

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    private String previousText = "";

    @Override
    public void afterTextChanged(Editable s) {
        if (PARTIAl_IP_ADDRESS.matcher(s).matches())
            previousText = s.toString();
        else
            s.replace(0, s.length(), previousText);
    }
}
