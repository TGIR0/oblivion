package org.bepass.oblivion.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.bepass.oblivion.R;
import org.bepass.oblivion.base.BaseActivity;
import org.bepass.oblivion.databinding.ActivityLogBinding;
import org.bepass.oblivion.utils.ISPUtils;
import org.bepass.oblivion.utils.ThemeHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public class LogActivity extends BaseActivity<ActivityLogBinding> {

    private static final String TAG = "LogActivity";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isUserScrollingUp = false;
    private Runnable logUpdater;
    private FrameLayout progressBar;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_log;
    }

    @Override
    protected int getStatusBarColor() {
        return R.color.status_bar_color;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Update background based on current theme
        ThemeHelper.getInstance().updateActivityBackground(binding.getRoot());

        // Initialize the ProgressBar
        progressBar = findViewById(R.id.progress_container);
        binding.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.copytoclip.setOnClickListener(v -> copyLast100LinesToClipboard());
        setupScrollListener();

        logUpdater = new Runnable() {
            @Override
            public void run() {
                readLogsFromFile();
                handler.postDelayed(this, 2000); // Refresh every 2 seconds
            }
        };
    }

    private void setupScrollListener() {
        binding.logScrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            int scrollY = binding.logScrollView.getScrollY();
            int maxScrollY = binding.logs.getHeight() - binding.logScrollView.getHeight();
            isUserScrollingUp = scrollY < maxScrollY;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(logUpdater);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(logUpdater);
    }

    private void readLogsFromFile() {
        if (!getFileStreamPath("logs.txt").exists()) {
            runOnUiThread(() -> {
                binding.logs.setText("");
                if (!isUserScrollingUp) {
                    binding.logScrollView.post(() -> binding.logScrollView.fullScroll(ScrollView.FOCUS_DOWN));
                }
            });
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(openFileInput("logs.txt")))) {
            SpannableStringBuilder sb = new SpannableStringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                int color = Color.BLACK; // Default Info - Changed to BLACK for visibility on light theme
                String lower = line.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("error") || lower.contains("fail") || lower.contains("[e]")) {
                    color = Color.RED;
                } else if (lower.contains("warn") || lower.contains("[w]")) {
                    color = Color.parseColor("#FFA500"); // Orange for warning
                } else if (lower.contains("debug") || lower.contains("[d]")) {
                     color = Color.DKGRAY;
                }
                
                int start = sb.length();
                sb.append(line).append("\n");
                sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), 0);
            }

            runOnUiThread(() -> {
                binding.logs.setText(sb);
                if (!isUserScrollingUp) {
                    binding.logScrollView.post(() -> binding.logScrollView.fullScroll(ScrollView.FOCUS_DOWN));
                }
            });
        } catch (IOException e) {
            // Swallow or log quietly
        }
    }

    private void copyLast100LinesToClipboard() {
        // Show progress bar
        progressBar.setVisibility(View.VISIBLE);

        ISPUtils.fetchISPInfo(new ISPUtils.ISPCallback() {
            @Override
            public void onISPInfoReceived(@NonNull String isp) {
                runOnUiThread(() -> {
                    // Hide progress bar
                    progressBar.setVisibility(View.GONE);

                    String logText = binding.logs.getText().toString();
                    String[] logLines = logText.split("\n");
                    int totalLines = logLines.length;

                    // Use Deque to efficiently get the last 100 lines
                    Deque<String> last100Lines = new ArrayDeque<>(100);
                    last100Lines.addAll(Arrays.asList(logLines).subList(Math.max(0, totalLines - 100), totalLines));

                    StringBuilder sb = new StringBuilder();
                    for (String line : last100Lines) {
                        sb.append(line).append("\n");
                    }

                    // Add ISP information
                    String ispText = (isp == null || isp.isEmpty()) ? "Unknown" : isp;
                    sb.append("\n=====\nISP: ").append(ispText).append("\n");

                    String last100Log = sb.toString();
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Log", last100Log);
                    clipboard.setPrimaryClip(clip);

                    showCopiedToClipboardToast();
                });
            }

            @Override
            public void onError(@NonNull Exception e) {
                runOnUiThread(() -> {
                    // Hide progress bar
                    progressBar.setVisibility(View.GONE);

                    Log.e(TAG, "Error fetching ISP information", e);
                    Toast.makeText(LogActivity.this, "Error fetching ISP information.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showCopiedToClipboardToast() {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast, findViewById(R.id.toast_layout));

        Toast toast = new Toast(getApplicationContext());
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(layout);
        toast.show();
    }
}