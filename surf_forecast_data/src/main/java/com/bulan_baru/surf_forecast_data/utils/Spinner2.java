package com.bulan_baru.surf_forecast_data.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Spinner;

public class Spinner2 extends Spinner {
    // private static final String TAG = "CustomSpinner";
    private OnSpinnerEventsListener mListener;
    private boolean mOpenInitiated = false;

    public Spinner2(Context context, AttributeSet attrs, int defStyleAttr, int mode) {
        super(context, attrs, defStyleAttr, mode);
    }

    public Spinner2(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public Spinner2(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public Spinner2(Context context, int mode) {
        super(context, mode);
    }

    public Spinner2(Context context) {
        super(context);
    }

    public interface OnSpinnerEventsListener {

        void onSpinnerOpened(Spinner spin);

        void onSpinnerClosed(Spinner spin);

    }

    @Override
    public boolean performClick() {
        // register that the Spinner was opened so we have a status
        // indicator for the activity(which may lose focus for some other
        // reasons)
        mOpenInitiated = true;
        if (mListener != null) {
            mListener.onSpinnerOpened(this);
        }
        return super.performClick();
    }

    public void setSpinnerEventsListener(OnSpinnerEventsListener onSpinnerEventsListener) {
        mListener = onSpinnerEventsListener;
    }

    /**
     * Propagate the closed Spinner event to the listener from outside.
     */
    public void performClosedEvent() {
        mOpenInitiated = false;
        if (mListener != null) {
            mListener.onSpinnerClosed(this);
        }
    }

    /**
     * A boolean flag indicating that the Spinner triggered an open event.
     *
     * @return true for opened Spinner
     */
    public boolean isOpen(){
        return mOpenInitiated;
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (isOpen() && hasWindowFocus) {
            performClosedEvent();
        }
    }

    /**
     * Use this to directly close the spinner
     */

    public void close(){
        //performClosedEvent();
        onDetachedFromWindow();
    }
}
