package moe.shizuku.manager.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.preference.Preference;

/**
 * Integer-backed list preference for display mode.
 *
 * Earlier builds used generated R.styleable constants from the Rikka
 * preference theme, which are not part of this standalone Android project.
 * The replacement preserves the integer preference contract without
 * relying on private library styleables or internal PopupWindow resources.
 */
public class IntegerSimpleMenuPreference extends Preference {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private CharSequence[] entries = new CharSequence[0];
    private int[] entryValues = new int[0];
    private int value;
    private boolean valueSet;

    public IntegerSimpleMenuPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        loadEntries(context, attrs);
    }

    public IntegerSimpleMenuPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public IntegerSimpleMenuPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IntegerSimpleMenuPreference(Context context) {
        this(context, null);
    }

    private void loadEntries(Context context, AttributeSet attrs) {
        if (attrs == null) return;
        int entriesId = attrs.getAttributeResourceValue(ANDROID_NS, "entries", 0);
        int valuesId = attrs.getAttributeResourceValue(ANDROID_NS, "entryValues", 0);
        if (entriesId != 0) entries = context.getResources().getTextArray(entriesId);
        if (valuesId != 0) entryValues = context.getResources().getIntArray(valuesId);
    }

    @Override
    protected void onClick() {
        if (entries.length == 0 || entryValues.length == 0) return;
        new AlertDialog.Builder(getContext())
                .setTitle(getTitle())
                .setSingleChoiceItems(entries, findIndexOfValue(value), (dialog, index) -> {
                    if (index >= 0 && index < entryValues.length) {
                        int newValue = entryValues[index];
                        if (callChangeListener(newValue)) setValue(newValue);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void setEntries(CharSequence[] values) {
        entries = values == null ? new CharSequence[0] : values;
        notifyChanged();
    }

    public void setEntries(@ArrayRes int resId) {
        setEntries(getContext().getResources().getTextArray(resId));
    }

    public CharSequence[] getEntries() {
        return entries;
    }

    public void setEntryValues(int[] values) {
        entryValues = values == null ? new int[0] : values;
        notifyChanged();
    }

    public void setEntryValues(@ArrayRes int resId) {
        setEntryValues(getContext().getResources().getIntArray(resId));
    }

    public int[] getEntryValues() {
        return entryValues;
    }

    public void setValue(int newValue) {
        boolean changed = !valueSet || value != newValue;
        value = newValue;
        valueSet = true;
        persistInt(newValue);
        if (changed) notifyChanged();
    }

    public int getValue() {
        return value;
    }

    public void setValueIndex(int index) {
        if (index >= 0 && index < entryValues.length) setValue(entryValues[index]);
    }

    public int findIndexOfValue(int match) {
        for (int i = 0; i < entryValues.length; i++) {
            if (entryValues[i] == match) return i;
        }
        return -1;
    }

    public CharSequence getEntry() {
        int index = findIndexOfValue(value);
        return index >= 0 && index < entries.length ? entries[index] : null;
    }

    @Override
    public CharSequence getSummary() {
        CharSequence summary = super.getSummary();
        if (summary == null) return getEntry();
        CharSequence current = getEntry();
        return summary.toString().replace("%1$s", current == null ? "" : current.toString())
                .replace("%s", current == null ? "" : current.toString());
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, 0);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        int initial = defaultValue instanceof Integer ? (Integer) defaultValue : 0;
        setValue(getPersistedInt(initial));
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable state = super.onSaveInstanceState();
        if (isPersistent()) return state;
        SavedState saved = new SavedState(state);
        saved.value = value;
        return saved;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState saved = (SavedState) state;
        super.onRestoreInstanceState(saved.getSuperState());
        setValue(saved.value);
    }

    private static class SavedState extends BaseSavedState {
        int value;

        SavedState(Parcelable superState) { super(superState); }
        SavedState(Parcel in) {
            super(in);
            value = in.readInt();
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(value);
        }

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            @Override public SavedState createFromParcel(Parcel in) { return new SavedState(in); }
            @Override public SavedState[] newArray(int size) { return new SavedState[size]; }
        };
    }
}
