package com.termux.app.fragments.settings.termux;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

@Keep
public class TerminalViewPreferencesFragment extends PreferenceFragmentCompat {

    private static final int REQUEST_CODE_PICK_BG = 9001;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TerminalViewPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_terminal_view_preferences, rootKey);

        Preference pickBgPref = findPreference("custom_bg_picker");
        if (pickBgPref != null) {
            pickBgPref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_CODE_PICK_BG);
                return true;
            });
        }

        Preference removeBgPref = findPreference("custom_bg_remove");
        if (removeBgPref != null) {
            removeBgPref.setOnPreferenceClickListener(preference -> {
                TerminalViewPreferencesDataStore.getInstance(context).removeBackgroundImage();
                Toast.makeText(context, "बैकग्राउंड वॉलपेपर हटा दिया गया है", Toast.LENGTH_SHORT).show();
                return true;
            });
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE_PICK_BG || resultCode != Activity.RESULT_OK) return;
        if (data == null || data.getData() == null) return;

        Context context = getContext();
        if (context == null) return;

        if (TerminalViewPreferencesDataStore.getInstance(context).saveBackgroundImage(data.getData())) {
            Toast.makeText(context, "फोटो सेट हो गई!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "इमेज लोड करने में विफल!", Toast.LENGTH_SHORT).show();
        }
    }

}

class TerminalViewPreferencesDataStore extends PreferenceDataStore {

    private static final String BACKGROUND_IMAGE_FILE_NAME = "terminal_background.img";

    private final Context mContext;
    private final TermuxAppSharedPreferences mPreferences;
    /** Custom terminal display preferences are stored in the default shared preferences. */
    private final SharedPreferences mDisplayPreferences;

    private static TerminalViewPreferencesDataStore mInstance;

    private TerminalViewPreferencesDataStore(Context context) {
        mContext = context;
        mPreferences = TermuxAppSharedPreferences.build(context, true);
        mDisplayPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static synchronized TerminalViewPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TerminalViewPreferencesDataStore(context);
        }
        return mInstance;
    }

    private File getBackgroundImageFile() {
        return new File(mContext.getFilesDir(), BACKGROUND_IMAGE_FILE_NAME);
    }

    boolean saveBackgroundImage(Uri uri) {
        File outputFile = getBackgroundImageFile();
        try (InputStream input = mContext.getContentResolver().openInputStream(uri);
             OutputStream output = new FileOutputStream(outputFile)) {
            if (input == null) throw new IllegalStateException("Unable to open the selected image");

            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > 0) output.write(buffer, 0, read);
            output.flush();

            // The picker URI permission may not survive a restart, so the terminal reads the
            // private copy of the image instead.
            mDisplayPreferences.edit()
                .putString(TERMUX_APP.KEY_CUSTOM_BACKGROUND_IMAGE_PATH, outputFile.getAbsolutePath())
                .apply();
            return true;
        } catch (Exception e) {
            outputFile.delete();
            return false;
        }
    }

    void removeBackgroundImage() {
        mDisplayPreferences.edit().putString(TERMUX_APP.KEY_CUSTOM_BACKGROUND_IMAGE_PATH, "").apply();
        getBackgroundImageFile().delete();
    }



    @Override
    public void putBoolean(String key, boolean value) {
        if (mPreferences == null) return;
        if (key == null) return;

        switch (key) {
            case "terminal_margin_adjustment":
                    mPreferences.setTerminalMarginAdjustment(value);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (mPreferences == null) return false;

        switch (key) {
            case "terminal_margin_adjustment":
                return mPreferences.isTerminalMarginAdjustmentEnabled();
            default:
                return false;
        }
    }

    @Override
    public void putString(String key, @Nullable String value) {
        if (key == null) return;

        if (TermuxPreferenceConstants.TERMUX_APP.KEY_CUSTOM_BACKGROUND_IMAGE_PATH.equals(key)) {
            mDisplayPreferences.edit().putString(key, value).apply();
        } else if (TermuxPreferenceConstants.TERMUX_APP.KEY_TEXT_SIZE.equals(key)) {
            // The text size list stores its value as a string, while the terminal reads an int.
            try {
                if (value != null)
                    mDisplayPreferences.edit().putInt(key, Integer.parseInt(value)).apply();
            } catch (NumberFormatException e) {
                // Ignore an invalid text size value.
            }
        }
    }

    @Override
    @Nullable
    public String getString(String key, @Nullable String defValue) {
        if (TermuxPreferenceConstants.TERMUX_APP.KEY_CUSTOM_BACKGROUND_IMAGE_PATH.equals(key)) {
            return mDisplayPreferences.getString(key, defValue);
        } else if (TermuxPreferenceConstants.TERMUX_APP.KEY_TEXT_SIZE.equals(key)) {
            return String.valueOf(mDisplayPreferences.getInt(key, defValue == null ? 12 : Integer.parseInt(defValue)));
        }
        return defValue;
    }

    @Override
    public void putInt(String key, int value) {
        if (key == null) return;

        if (TermuxPreferenceConstants.TERMUX_APP.KEY_TEXT_SIZE.equals(key) ||
            TermuxPreferenceConstants.TERMUX_APP.KEY_BACKGROUND_IMAGE_OPACITY.equals(key)) {
            mDisplayPreferences.edit().putInt(key, value).apply();
        }
    }

    @Override
    public int getInt(String key, int defValue) {
        if (TermuxPreferenceConstants.TERMUX_APP.KEY_TEXT_SIZE.equals(key) ||
            TermuxPreferenceConstants.TERMUX_APP.KEY_BACKGROUND_IMAGE_OPACITY.equals(key)) {
            return mDisplayPreferences.getInt(key, defValue);
        }
        return defValue;
    }

}
