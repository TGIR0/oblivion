package org.bepass.oblivion.component;

import android.content.Context;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.os.Build;

import com.google.android.material.materialswitch.MaterialSwitch;

public class TouchAwareSwitch extends MaterialSwitch {

    private OnCheckedChangeListener mListener;
    // پرچم برای تشخیص تغییرات سیستمی از تغییرات کاربر
    private boolean isProgrammaticChange = false;

    public TouchAwareSwitch(Context context) {
        super(context);
    }

    public TouchAwareSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TouchAwareSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        // نگه داشتن رفرنس لیسنر برای مدیریت داخلی
        this.mListener = listener;
        super.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mListener != null) {
                // اگر تغییر توسط متد setChecked(..., false) انجام شده باشد، لیسنر صدا زده نمی‌شود
                if (!isProgrammaticChange) {
                    mListener.onCheckedChanged(buttonView, isChecked);
                    // اضافه کردن ویبره ریز هنگام تاچ کاربر (تجربه کاربری بهتر)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (isChecked) {
                            performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                        } else {
                            performHapticFeedback(HapticFeedbackConstants.REJECT);
                        }
                    }
                }
            }
        });
    }

    /**
     * تغییر وضعیت سوییچ با قابلیت کنترل فراخوانی لیسنر
     *
     * @param checked وضعیت جدید (روشن/خاموش)
     * @param notifyListener آیا لیسنر با این تغییر صدا زده شود؟
     */
    public void setChecked(boolean checked, boolean notifyListener) {
        if (notifyListener) {
            isProgrammaticChange = false;
            super.setChecked(checked);
        } else {
            isProgrammaticChange = true;
            super.setChecked(checked);
            isProgrammaticChange = false;
        }
    }

    @Override
    public void setChecked(boolean checked) {
        // رفتار پیش‌فرض: لیسنر صدا زده می‌شود
        super.setChecked(checked);
    }
}