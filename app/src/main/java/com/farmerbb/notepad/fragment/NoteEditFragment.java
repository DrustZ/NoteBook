/* Copyright 2014 Braden Farmer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.farmerbb.notepad.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Magnifier;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.farmerbb.notepad.activity.MainActivity;
import com.farmerbb.notepad.R;
import com.farmerbb.notepad.service.FloatingButtonService;
import com.farmerbb.notepad.util.ExpLogger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.view.Gravity.NO_GRAVITY;

public class NoteEditFragment extends Fragment implements
        View.OnTouchListener, TextWatcher {

    final String TAG = "[Log]";
    private EditText noteContents;
    private ScrollView mscrollView;

    //post
    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    OkHttpClient client = new OkHttpClient();

    /**
     * touch related vars
     */
    private String correct_option = "drag";
    private Magnifier magnifier = null;
    private VelocityTracker mVelocityTracker = null;
    private boolean correction_begin = false; // if entered the correction mode
    private boolean undo_begin = false; // if undo gesture performed
    private float release_x = -1;
    private float release_y = -1;
    private float touch_down_x = -1;
    private float touch_down_y = -1;
    private long touch_down_time = 0;

    /**
     * correction related vars
     */
    private int span_begin = -1; //error begin position
    private int span_end = -1; //error end position
    private int sent_begin = -1;
    private int sent_end = -1;

    private String correction = null;
    private String last_content = null; //for undo, text before apply the correction
    private String correct_content = null; // for undo, text after apply the correction
    private int last_span_begin = -1;
    private int last_span_end = -1;

    private String sc_original_content = null;
    private boolean smart_correction_begin = false;
    private int sc_current_idx = -1;
    private int sc_correction_range_start = -1;
    private JSONArray sc_starts = null;
    private JSONArray sc_ends = null;
    private JSONArray sc_corrections = null;

    /**
     * correction related views
     */
    private FloatButtonReceiver floatButtonReceiver = null;
    private PopupWindow popupWindow = null;
    private TextView indiactorView = null;
    // Span to set text color to some RGB value
    BackgroundColorSpan bcs = new BackgroundColorSpan(Color.rgb(255, 255, 51));
    SpannableStringBuilder sb = new SpannableStringBuilder();

    /**
     * experiment testing mode
     */
    Boolean testingmode = false;
    Integer current_testing = 0;
    ArrayList<String[]> teststrings = new ArrayList<String[]>();
    ExpLogger logger = null;
    long post_time = 0;
    long start_post_time = 0;

    String filename = String.valueOf(System.currentTimeMillis());
    String contentsOnLoad = "";
    int length = 0;
    long draftName;
    boolean isSavedNote = false;
    String contents;
    boolean directEdit = false;

    // Receiver used to close fragment when a note is deleted
    public class DeleteNotesReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String[] filesToDelete = intent.getStringArrayExtra("files");

            for(Object file : filesToDelete) {
                if(filename.equals(file)) {
                    // Hide soft keyboard
                    EditText editText = getActivity().findViewById(R.id.editText1);
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);

                    // Add NoteListFragment or WelcomeFragment
                    Fragment fragment;
                    if(getActivity().findViewById(R.id.layoutMain).getTag().equals("main-layout-normal"))
                        fragment = new NoteListFragment();
                    else
                        fragment = new WelcomeFragment();

                    getFragmentManager()
                        .beginTransaction()
                        .replace(R.id.noteViewEdit, fragment, "NoteListFragment")
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                        .commit();
                }
            }
        }
    }

    IntentFilter filter = new IntentFilter("com.farmerbb.notepad.DELETE_NOTES");
    DeleteNotesReceiver receiver = new DeleteNotesReceiver();

    /* The activity that creates an instance of this fragment must
     * implement this interface in order to receive event call backs. */
    public interface Listener {
        void showBackButtonDialog(String filename);
        void showDeleteDialog();
        void showSaveButtonDialog();
        boolean isShareIntent();
        String loadNote(String filename) throws IOException;
        String loadNoteTitle(String filename) throws IOException;
        void exportNote(String filename);
        void printNote(String contentToPrint);
    }

    // Use this instance of the interface to deliver action events
    Listener listener;

    // Override the Fragment.onAttach() method to instantiate the Listener
    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the Listener so we can send events to the host
            listener = (Listener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                                         + " must implement Listener");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_note_edit, container, false);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Set values
        setRetainInstance(true);
        setHasOptionsMenu(true);

        // Show the Up button in the action bar.
        ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Animate elevation change
        if(getActivity() instanceof MainActivity
                && getActivity().findViewById(R.id.layoutMain).getTag().equals("main-layout-large")
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            LinearLayout noteViewEdit = getActivity().findViewById(R.id.noteViewEdit);
            LinearLayout noteList = getActivity().findViewById(R.id.noteList);

            noteList.animate().z(0f);
            if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                noteViewEdit.animate().z(getResources().getDimensionPixelSize(R.dimen.note_view_edit_elevation_land));
            else
                noteViewEdit.animate().z(getResources().getDimensionPixelSize(R.dimen.note_view_edit_elevation));
        }

        // Set up content view
        noteContents = getActivity().findViewById(R.id.editText1);
        noteContents.setOnTouchListener(this);
        magnifier = new Magnifier(noteContents);
        // Apply theme
        SharedPreferences pref = getActivity().getSharedPreferences(getActivity().getPackageName() + "_preferences", Context.MODE_PRIVATE);
        ScrollView scrollView = getActivity().findViewById(R.id.scrollView1);
        mscrollView = scrollView;
        String theme = pref.getString("theme", "light-sans");

        if(theme.contains("light")) {
            noteContents.setTextColor(ContextCompat.getColor(getActivity(), R.color.text_color_primary));
            noteContents.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.window_background));
            scrollView.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.window_background));
        }

        if(theme.contains("dark")) {
            noteContents.setTextColor(ContextCompat.getColor(getActivity(), R.color.text_color_primary_dark));
            noteContents.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.window_background_dark));
            scrollView.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.window_background_dark));
        }

        if(theme.contains("sans"))
            noteContents.setTypeface(Typeface.SANS_SERIF);

        if(theme.contains("serif"))
            noteContents.setTypeface(Typeface.SERIF);

        if(theme.contains("monospace"))
            noteContents.setTypeface(Typeface.MONOSPACE);

        switch(pref.getString("font_size", "normal")) {
            case "smallest":
                noteContents.setTextSize(12);
                break;
            case "small":
                noteContents.setTextSize(14);
                break;
            case "normal":
                noteContents.setTextSize(16);
                break;
            case "large":
                noteContents.setTextSize(18);
                break;
            case "largest":
                noteContents.setTextSize(20);
                break;
        }

        // Get filename
        try {
            if(!getArguments().getString("filename").equals("new")) {
                filename = getArguments().getString("filename");
                if(!filename.equals("draft"))
                    isSavedNote = true;
            } else {
                testingmode = getArguments().getBoolean("testing");
            }
        } catch (NullPointerException e) {
            filename = "new";
        }

        // Load note from existing file
        if(isSavedNote) {
            try {
                contentsOnLoad = listener.loadNote(filename);
            } catch (IOException e) {
                showToast(R.string.error_loading_note);
                finish(null);
            }

            // Set TextView contents
            length = contentsOnLoad.length();
            noteContents.setText(contentsOnLoad);

            if(!pref.getBoolean("direct_edit", false))
                noteContents.setSelection(length, length);
        } else if(filename.equals("draft")) {
            SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
            String draftContents = sharedPref.getString("draft-contents", null);
            length = draftContents.length();
            noteContents.setText(draftContents);

            if(!pref.getBoolean("direct_edit", false))
                noteContents.setSelection(length, length);
        }

        indiactorView = new TextView(this.getContext());
        indiactorView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        indiactorView.setTypeface(Typeface.SERIF, Typeface.BOLD);
        indiactorView.setTextSize(18);
        indiactorView.setTextColor(0xbf339966);
        indiactorView.setBackgroundColor(Color.argb(100, 200, 200, 200));
        popupWindow = new PopupWindow(indiactorView);
        popupWindow.setFocusable(false);

        // Show soft keyboard
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(noteContents, InputMethodManager.SHOW_IMPLICIT);

        prepareTestingPhrases();
    }

    @Override
    public void onPause() {
        super.onPause();

        // Disable saving drafts if user launched Notepad through a share intent
        if(!listener.isShareIntent() && !isRemoving()) {
            // Set current note contents to a String
            noteContents = getActivity().findViewById(R.id.editText1);
            contents = noteContents.getText().toString();

            if(!contents.equals("")) {
                SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);

                // Save filename to draft-name preference
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putLong("draft-name", Long.parseLong(filename));
                editor.putBoolean("is-saved-note", isSavedNote);

                // Write draft to SharedPreferences
                editor.putString("draft-contents", contents);
                editor.apply();

                // Show toast notification
                showToast(R.string.draft_saved);
            }
        }
//        if (floatButtonReceiver != null) getActivity().unregisterReceiver(floatButtonReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();

        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        // Disable restoring drafts if user launched Notepad through a share intent
        if(!listener.isShareIntent()) {
            if(filename.equals("draft")) {

                // Restore draft preferences
                draftName = sharedPref.getLong("draft-name", 0);
                isSavedNote = sharedPref.getBoolean("is-saved-note", false);

                // Restore filename of draft
                filename = Long.toString(draftName);

                // Reload old file into memory, so that correct contentsOnLoad is set
                if(isSavedNote) {
                    try {
                        contentsOnLoad = listener.loadNote(filename);
                    } catch (IOException e) {
                        showToast(R.string.error_loading_note);
                        finish(null);
                    }
                } else
                    contentsOnLoad = "";

                // Notify the user that a draft has been restored
                showToast(R.string.draft_restored);
            }

            // Clear draft preferences
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.remove("draft-name");
            editor.remove("is-saved-note");
            editor.remove("draft-contents");
            editor.apply();
        }

        // Change window title
        String title;

        if(isSavedNote)
            try {
                title = listener.loadNoteTitle(filename);
            } catch (IOException e) {
                title = getResources().getString(R.string.edit_note);
            }
        else
            title = getResources().getString(R.string.action_new);

        getActivity().setTitle(title);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Bitmap bitmap = ((BitmapDrawable) ContextCompat.getDrawable(getActivity(), R.drawable.ic_recents_logo)).getBitmap();

            ActivityManager.TaskDescription taskDescription = new ActivityManager.TaskDescription(title, bitmap, ContextCompat.getColor(getActivity(), R.color.primary));
            getActivity().setTaskDescription(taskDescription);
        }

        SharedPreferences pref = getActivity().getSharedPreferences(getActivity().getPackageName() + "_preferences", Context.MODE_PRIVATE);
        directEdit = pref.getBoolean("direct_edit", false);
        correct_option = pref.getString("correction_method", "drag");

        if (testingmode && current_testing < teststrings.size()) {
            noteContents.setText(teststrings.get(current_testing)[0]);
            noteContents.setSelection(teststrings.get(current_testing)[0].length());
            logger = new ExpLogger();
            logger.logStart();
            logger.startTask(0);
        }

        if (correct_option.equals("smart") || testingmode) {
            //apply touch listener
            noteContents.addTextChangedListener(this);
//            if (floatButtonReceiver == null) floatButtonReceiver = new FloatButtonReceiver();
//            getActivity().registerReceiver(floatButtonReceiver, new IntentFilter(FloatingButtonService.FLOAT_BUTTON_INTENT));
        } else {
            noteContents.removeTextChangedListener(this);
        }
    }

    // Register and unregister DeleteNotesReceiver
    @Override
    public void onStart() {
        super.onStart();

        if(!listener.isShareIntent())
            LocalBroadcastManager.getInstance(getActivity()).registerReceiver(receiver, filter);
    }

    @Override
    public void onStop() {
        super.onStop();

        if(!listener.isShareIntent())
            LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(receiver);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.note_edit, menu);

        if(listener.isShareIntent() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            menu.removeItem(R.id.action_export);
            menu.removeItem(R.id.action_print);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Hide soft keyboard
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getActivity().findViewById(R.id.editText1).getWindowToken(), 0);

        switch (item.getItemId()) {
            case android.R.id.home:
                // Override default Android "up" behavior to instead mimic the back button
                getActivity().onBackPressed();
                return true;

                // Save button
            case R.id.action_save:
                // Get current note contents from EditText
                noteContents = getActivity().findViewById(R.id.editText1);
                contents = noteContents.getText().toString();

                // If EditText is empty, show toast informing user to enter some text
                if(contents.equals(""))
                    showToast(R.string.empty_note);
                else if(directEdit)
                    getActivity().onBackPressed();
                else {
                    // If no changes were made since last save, switch back to NoteViewFragment
                    if(contentsOnLoad.equals(noteContents.getText().toString())) {
                        Bundle bundle = new Bundle();
                        bundle.putString("filename", filename);

                        Fragment fragment = new NoteViewFragment();
                        fragment.setArguments(bundle);

                        getFragmentManager()
                                .beginTransaction()
                                .replace(R.id.noteViewEdit, fragment, "NoteViewFragment")
                                .commit();
                    } else {
                        SharedPreferences pref = getActivity().getSharedPreferences(getActivity().getPackageName() + "_preferences", Context.MODE_PRIVATE);
                        if(pref.getBoolean("show_dialogs", true)) {
                            // Show save button dialog
                            listener.showSaveButtonDialog();
                        } else {
                            try {
                                Intent intent = new Intent();
                                intent.putExtra(Intent.EXTRA_TEXT, noteContents.getText().toString());
                                this.getActivity().setResult(Activity.RESULT_OK, intent);
                                saveNote();

                                if(listener.isShareIntent())
                                    getActivity().finish();
                                else {
                                    Bundle bundle = new Bundle();
                                    bundle.putString("filename", filename);

                                    Fragment fragment = new NoteViewFragment();
                                    fragment.setArguments(bundle);

                                    getFragmentManager()
                                            .beginTransaction()
                                            .replace(R.id.noteViewEdit, fragment, "NoteViewFragment")
                                            .commit();
                                }
                            } catch (IOException e) {
                                // Show error message as toast if file fails to save
                                showToast(R.string.failed_to_save);
                            }
                        }
                    }
                }
                return true;

                // Delete button
            case R.id.action_delete:
                listener.showDeleteDialog();
                return true;

                // Share menu item
            case R.id.action_share:
                // Set current note contents to a String
                contents = noteContents.getText().toString();

                // If EditText is empty, show toast informing user to enter some text
                if(contents.equals(""))
                    showToast(R.string.empty_note);
                else {
                    // Send a share intent
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_SEND);
                    intent.putExtra(Intent.EXTRA_TEXT, contents);
                    intent.setType("text/plain");

                    // Verify that the intent will resolve to an activity, and send
                    if(intent.resolveActivity(getActivity().getPackageManager()) != null)
                        startActivity(Intent.createChooser(intent, getResources().getText(R.string.send_to)));
                }

                return true;

            // Export menu item
            case R.id.action_export:
                // Set current note contents to a String
                contents = noteContents.getText().toString();

                // If EditText is empty, show toast informing user to enter some text
                if(contents.equals(""))
                    showToast(R.string.empty_note);
                else {
                    String currentFilename = filename;
                    filename = "exported_note";

                    try {
                        saveNote();
                    } catch (IOException e) { /* Gracefully fail */ }

                    filename = currentFilename;

                    listener.exportNote("exported_note");
                }

                return true;

            // Print menu item
            case R.id.action_print:
                // Set current note contents to a String
                contents = noteContents.getText().toString();

                // If EditText is empty, show toast informing user to enter some text
                if(contents.equals(""))
                    showToast(R.string.empty_note);
                else
                    listener.printNote(contents);

                return true;
            //disable long pressing
            case R.id.action_restart:
                if (current_testing > 0) {
                    current_testing -= 1;
                    sb.clear();
                    sb.clearSpans();
                    sb.append(teststrings.get(current_testing)[0]);
                    setTextWithoutWatcher(sb);
                    testingDialog = null;
                    logger.restartTask(current_testing);
                    noteContents.setSelection(teststrings.get(current_testing)[0].length());
                }
                return true;
            case R.id.action_savelog:
                //save log
                if (testingmode) {
                    logger.logPostTime(post_time);
                    post_time = 0;
                    logger.finishTask("");
                    final EditText editText = new EditText(getActivity());
                    AlertDialog.Builder inputDialog =
                            new AlertDialog.Builder(getActivity());
                    inputDialog.setTitle("Log File Name").setView(editText);
                    inputDialog.setPositiveButton("Save", (v, w) -> {
                        logger.logEnd(editText.getText().toString().trim());
                        Toast.makeText(getActivity(),
                                editText.getText().toString().trim()+" Saved",
                                Toast.LENGTH_SHORT).show();
                    });
                    testingDialog = inputDialog.create();
                    testingDialog.show();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Funcs recieves commands
     * @param x
     * @param y
     * @param correctstr
     */
    public void onReceivedCorrection(int x, int y, String correctstr){
        Layout layout = noteContents.getLayout();
        int [] location = new int[2];
        noteContents.getLocationInWindow(location);
        //rawXY to relative XY
        x = x - location[0];
        y = y - location[1];
        release_x = x;
        release_y = y;
        boolean hascorrection = getCorrection(noteContents.getText().toString());
        if (hascorrection) {
            executeAutoCorrection(x, y, correctstr);
        }
    }

    public void onReceivedUndo(){
        String content = noteContents.getText().toString();
        if (last_content != null && correct_content != null
        && correct_content.equals(content)){
            undoWithAnimation();
            if (testingmode && logger != null) {
                logger.logUndo();
            }
        }
        else {
            correct_content = null;
            last_content = null;
        }
    }

    public void onReceivedGesture(String gesture) {
        if (testingmode){
            logger.logGesture(gesture);
        }
    }

    public boolean getCorrection(String content){
        content = content.replaceAll("[^A-Za-z0-9 ]", " ");
        String tokens[] = content.split("[(\\r?\\n)\\s]+");
        if (tokens[tokens.length-1].length() > 0) {
            correction = tokens[tokens.length-1];
        }
        if (tokens.length > 1 && correction.length() > 0)
            return true;
        return false;
    }

    public void onReceivedSmartCorrection(int command) {
        if (!correct_option.equals("smart")) return;
        switch (command){
            //single tap command
            case 1:
                if (!smart_correction_begin) {
                    Log.e(TAG, "smart begin!");
                    noteContents.clearFocus();
                    sc_original_content = noteContents.getText().toString();
                    Boolean hasCorrection = getCorrection(sc_original_content);
                    // there is correction to make!
                    if (hasCorrection){
                        smart_correction_begin = true;
                        sc_correction_range_start = Math.max(sc_original_content.lastIndexOf(correction) - 1000, 0);
                        sc_starts = null;
                        new PostSmartCorrectionTask().execute(sc_original_content.substring(sc_correction_range_start, sc_original_content.lastIndexOf(correction)) );
                    }
                } else {
                    Log.e(TAG, "smart end!");
                    smart_correction_begin = false;
                    if (sc_starts != null && sc_ends != null) {
                        //correct it for ya
                        correction = sc_corrections.optString(sc_current_idx);
                        sb.clear();
                        sb.clearSpans();
                        sb.append(sc_original_content);
                        sc_original_content = null;
                        setTextWithoutWatcher(sb);
                        replaceWithAnimation();
                    }
                }
                break;
            //left drag
            case 2:
                if (smart_correction_begin){
                    //go previous
                    noteContents.clearFocus();
                    if (sc_starts != null && sc_current_idx+1 < sc_starts.length()) {
                        sc_current_idx += 1;
                        span_begin = sc_correction_range_start + sc_starts.optInt(sc_current_idx);
                        span_end = sc_correction_range_start + sc_ends.optInt(sc_current_idx);
                        highlightStringInRange(span_begin, span_end);
                        if (testingmode && logger != null) {
                            logger.logSwipe("left");
                        }
                    }
                }
                break;
            //right drag
            default:
                if (smart_correction_begin) {
                    noteContents.clearFocus();
                    if (sc_current_idx-1 >= 0) {
                        sc_current_idx -= 1;
                        span_begin = sc_correction_range_start + sc_starts.optInt(sc_current_idx);
                        span_end = sc_correction_range_start + sc_ends.optInt(sc_current_idx);
                        highlightStringInRange(span_begin, span_end);
                        if (testingmode && logger != null) {
                            logger.logSwipe("right");
                        }
                    }
                }
                break;
        }
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        if (correct_option.equals("smart") && smart_correction_begin) {
            //text changed by user, cancel the correction process
            if (getActivity().getCurrentFocus() == noteContents){
                cancelSmartHighlight();
                noteContents.setSelection(noteContents.length());
            }
        }
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

    AlertDialog testingDialog = null;
    @Override
    public void afterTextChanged(Editable editable) {
        if (testingmode){
            logger.logChange(editable.toString());
            String current_content = editable.toString().trim();
            if (current_content.equals(teststrings.get(current_testing)[1].trim()) && testingDialog == null){
                logger.logPostTime(post_time);
                post_time = 0;
                logger.finishTask(teststrings.get(current_testing)[2]);
                final AlertDialog.Builder normalDialog =
                        new AlertDialog.Builder(getActivity());
                if (current_testing == teststrings.size()-1){
                    normalDialog.setTitle("Task Finished");
                    normalDialog.setMessage("Click OK to finish");
                } else {
                    normalDialog.setTitle("Task "+(current_testing+1)+ " done");
                    normalDialog.setMessage("Click OK to go to next task");
                }
                normalDialog.setPositiveButton("OK", (v,w) -> {
                    current_testing += 1;
                    if (current_testing < teststrings.size()){
                        sb.clear();
                        sb.clearSpans();
                        sb.append(teststrings.get(current_testing)[0]);
                        setTextWithoutWatcher(sb);
                        noteContents.setSelection(teststrings.get(current_testing)[0].length());
                        testingDialog = null;
                        logger.startTask(current_testing);
                    } else {
                        logger.logEnd("");
                    }
                });

                normalDialog.setCancelable(false);
                testingDialog = normalDialog.create();
                testingDialog.show();
            }
        }
    }

    /***
     * Touch processing functions
     * @param
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (v == noteContents) {
            switch (event.getAction()){
                case MotionEvent.ACTION_DOWN:
                    correction = null;
                    if (correct_option.equals("smart") && smart_correction_begin){
                        cancelSmartHighlight();
                    } else
                    if (correct_option.equals("plain")) {
                        if (mVelocityTracker == null) {
                            // Retrieve a new VelocityTracker object to watch the velocity of a motion.
                            mVelocityTracker = VelocityTracker.obtain();
                            mVelocityTracker.addMovement(event);
                        } else {
                            // Reset the velocity tracker back to its initial state.
                            mVelocityTracker.clear();
                        }
                        float x = event.getX();
                        float y = event.getY();
                        touch_down_x = x;
                        touch_down_y = y;
                        String content = noteContents.getText().toString();
                        boolean hascorrection = getCorrection(content);
                        int correctionidx = -1;
                        if (hascorrection) {
                            correctionidx = content.lastIndexOf(correction);
                            Log.e(TAG, "correction : "+correction);
                            indiactorView.setText(correction);
                        }
                        //if there is text
                        if (correctionidx >= 0) {
                            float wordlen = getWordWidth(correction);
                            float wordendX = getLastLineWidth(correctionidx, correction.length()+correctionidx-1);
                            float wordstartX = wordendX - wordlen;
                            float wordendY = getContentHeight();
                            float wordstartY = wordendY - getLineHeight();
                            if (x <= wordendX + 100 && x >= wordstartX - 50
                                    && y >= wordstartY - 40 && y <= wordendY + 100) {
                                correction_begin = true;
                                undo_begin = false;
//                                Log.e(TAG, "start correction! on " + correction);
                                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.hideSoftInputFromWindow(noteContents.getWindowToken(), 0);
                                //                            Log.e(TAG, "scroll Y "+scrollview.getScrollY());
                                //                            Log.e(TAG, "last line width: "+ getLastLineWidth(getWordWidth(correction)) + " overall height "+getContentHeight());
                                //                            Log.e(TAG, "last line top: "+ (getContentHeight()-getLineHeight()) + " last word start "+(getLastLineWidth(getWordWidth(correction))-getWordWidth(correction)));
                                //                            Log.e(TAG, "touch x "+x + " y "+y);
                                return true;
                            }
//                            else if (x <= release_x + 150 && x >= release_x - 150
//                                    && y >= release_y - 150 && y <= release_y + 150
//                                    && last_content != null && correct_content != null
//                                    && correct_content.equals(content)) // undo criterion
//                            {
//                                touch_down_time = System.currentTimeMillis();
//                                undo_begin = true;
//                                return true;
//                            }
                            else {
                                undo_begin = false;
                            }
                        }
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (correct_option.equals("plain")) {
                        if (!correction_begin) break;

                        mVelocityTracker.addMovement(event);
                        mVelocityTracker.computeCurrentVelocity(1000); //pixel / sec
                        float vx = mVelocityTracker.getXVelocity();
                        float vy = mVelocityTracker.getYVelocity();
                        if (Math.sqrt(vx * vx + vy * vy) < 1000) {
                            int offset = getTextIndexOfXY(event.getX(), event.getY(), 50);

                            String content = noteContents.getText().toString();
                            int cursor_pos = noteContents.getSelectionStart();
                            if (getHighlightStringOnIndex(content, offset)) {
                                noteContents.setText(sb);
                            }
                            noteContents.setSelection(cursor_pos);
                            magnifier.show(event.getX(), event.getY() - 30);
                            if (popupWindow.isShowing()) {
                                popupWindow.update((int) event.getRawX() - (magnifier.getWidth() - 20) / 2, (int) (event.getRawY() - 190 - magnifier.getHeight()),
                                        magnifier.getWidth() - 20, magnifier.getHeight() - 55);
                            } else {
                                popupWindow.showAtLocation(noteContents, NO_GRAVITY, (int) event.getRawX() - (magnifier.getWidth() - 20) / 2, (int) (event.getRawY() - 190 - magnifier.getHeight()));
                            }
                        } else {
                            span_begin = -1;
                            span_end = -1;
                            int cursor_pos = noteContents.getSelectionStart();
                            noteContents.setText(noteContents.getText().toString());
                            magnifier.dismiss();
                            noteContents.setSelection(cursor_pos);
                        }
                        return true;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    release_x = event.getX();
                    release_y = event.getY();
                    Log.e(TAG, "receive update on touch x: " + release_x + " y: "+release_y );

                    if (correct_option.equals("plain")) {
                        if (!correction_begin && !undo_begin) break;
                        if (correction_begin ) {
                            //not correct too fast
                            if (event.getEventTime()-event.getDownTime() < 300){
                                noteContents.setText(noteContents.getText().toString());
                                endCorrection();
                                break;
                            }
                            if (correction != null && span_begin >= 0) {
                                replaceWithAnimation();
                            } else {
                                noteContents.setText(noteContents.getText().toString());
                                noteContents.setSelection(noteContents.getText().length());
                            }
                        } else if (undo_begin){
                            if (release_y > touch_down_y+50 && System.currentTimeMillis() - touch_down_time < 1000) {
                                    undoWithAnimation();
                            }
                            undo_begin = false;
                        }
                    }
                    endCorrection();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    endCorrection();
                    break;
            }
        }
        return false;
    }

    private void executeAutoCorrection(float x, float y, String correction_str) {
        if (!correct_option.equals("drag")) return;
//        int offset1 = getTextIndexOfXY(x, y, 70); //line 0
//        int offset2 = getTextIndexOfXY(x, y, 40); //line 1
//        int offset3 = getTextIndexOfXY(x, y, 0); //line 2
        String content = noteContents.getText().toString();
        List<Integer> offsets = new ArrayList<Integer>();
        int offset1 = getTextIndexOfXY(x, y, 0);
        int offset2 = 0;
        offsets.add(offset1);
        int yoff = 20;
        while (y - yoff > 0 && offsets.size() < 3){
//            Log.e(TAG, "executeAutoCorrection: yoff "+offset1 + " " +content.length());
            offset2 = getTextIndexOfXY(x, y, yoff);
            if (offset1 != offset2){
                offsets.add(offset2);
                offset1 = offset2;
            }
            yoff += 20;
        }
//        Log.e(TAG, "executeAutoCorrection: size"+offsets.size() );

        ArrayList<String> arr = new ArrayList<String>();

        for (int i = 0; i < offsets.size(); ++i) {
            String s = getSurroudningTextOfIndex(content, offsets.get(i));
            Log.e(TAG, "now have: "+s);
            if (s != null) {
                arr.add(s);
                arr.add(correction_str);
                arr.add(String.valueOf(sent_begin));
                arr.add(String.valueOf(sent_end));
            }
        }
        new PostCorrectionTask().execute(arr);
    }

    private void endCorrection() {
        releaseVelocityTracker();
        if (popupWindow != null)
            popupWindow.dismiss();
        if (magnifier != null)
            magnifier.dismiss();
        undo_begin = false;
        correction_begin = false;
    }

    /**
     * Http call
     */
    class PostCorrectionTask extends AsyncTask<ArrayList<String>, Void, Void> {

        private Exception exception;
        float bestprob = 0;
        String bestsent = null;
        String bestcor_sent = null;
        int bestsent_begin = -1;
        int bestsent_end = -1;
        int beststart = -1;
        int bestlen = -1;

        @Override
        protected Void doInBackground(ArrayList<String>... arrays) {
            try {
                ArrayList<String> arr = arrays[0];
                start_post_time = System.nanoTime();
                for (int i = 0; i < arr.size(); i += 4) {
                    String sent = arr.get(i);
                    String correction = arr.get(i+1);
                    Log.e(TAG, "sending correction"+i+" : "+sent );

                    int sent_begin = Integer.parseInt(arr.get(i+2));
                    int sent_end = Integer.parseInt(arr.get(i+3));
                    String jsonString = new JSONObject()
                            .put("smart", 0)
                            .put("sent", sent)
                            .put("correction", correction).toString();

                    RequestBody body = RequestBody.create(JSON, jsonString);
                    Request request = new Request.Builder()
                            .url("http://172.28.100.27:8765")
                            .post(body)
                            .build();
                    Response response = client.newCall(request).execute();

                    JSONObject jsonObj = new JSONObject(response.body().string());

                    int cor_start = jsonObj.getInt("start");
                    int cor_len = jsonObj.getInt("len");
                    float cor_prob = (float) jsonObj.getDouble("prob");
                    Log.e(TAG, "start : " + cor_start + " len : " + cor_len + " prob : " + cor_prob);
                    String cor_text = "";
                    if (jsonObj.has("sent")) {
                        cor_text = jsonObj.getString("sent");
                        Log.e(TAG, "corrected to :" + cor_text);
                    }

                    if (cor_prob > bestprob) {
                        // has to be within the rect of release point
                        if (offsetWithinRange(cor_start + sent_begin, release_x)) {
                            bestcor_sent = cor_text;
                            bestsent = sent;
                            bestsent_begin = sent_begin;
                            bestsent_end = sent_end;
                            beststart = cor_start + sent_begin; // the global start index
                            bestlen = cor_len;
                            bestprob = cor_prob;
                        }
                    }
                }
            } catch (Exception e){
                e.printStackTrace();
                exception = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Log.e(TAG, "best sent:"+bestsent +"\ncorr to  :"+bestcor_sent);
            if (bestprob > 0) {
                String content = noteContents.getText().toString();
                if (content == null) return;

                last_content = content;
                last_span_begin = beststart;
                last_span_end = content.substring(beststart+1).indexOf(' ');

                int lastidx = content.lastIndexOf(correction);
                if (lastidx < 0) return;
                post_time += System.nanoTime() - start_post_time;
                Log.e(TAG, "post time: "+ (post_time/1000000) );
                content = content.substring(0, lastidx);

                if (last_span_end == -1) { last_span_end = last_span_begin+1; }

                if (bestsent_end >= content.length()){
                    replaceWithAnimationInRange(content.substring(0, bestsent_begin) + bestcor_sent, beststart, bestlen);
                } else {
                    replaceWithAnimationInRange(content.substring(0, bestsent_begin) + bestcor_sent + content.substring(bestsent_end), beststart, bestlen);
                }
                noteContents.setSelection(noteContents.getText().length());
            } else {
                noteContents.setText(noteContents.getText().toString());
                noteContents.setSelection(noteContents.getText().length());
            }
        }
    }

    class PostSmartCorrectionTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... strings) {
            try {
                start_post_time = System.nanoTime();
                String sent = strings[0];
                String jsonString = new JSONObject()
                        .put("smart", 1)
                        .put("sent", sent)
                        .put("correction", correction).toString();

                RequestBody body = RequestBody.create(JSON, jsonString);
                Request request = new Request.Builder()
                        .url("http://172.28.100.27:8765")
                        .post(body)
                        .build();
                Response response = client.newCall(request).execute();
                Log.e(TAG, "doInBackground: "+response.toString());
                JSONObject jsonObj = new JSONObject(response.body().string());
                sc_starts = jsonObj.getJSONArray("starts");
                sc_ends = jsonObj.getJSONArray("ends");
                sc_corrections = jsonObj.getJSONArray("corrections");
                return null;
            } catch (Exception e){
                e.printStackTrace();
                Log.e(TAG, e.getLocalizedMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            post_time += System.nanoTime() - start_post_time;
            if (sc_starts != null && sc_ends != null && sc_starts.length() == sc_ends.length()) {
                //highlight the first correction
                sc_current_idx = 0;
                span_begin = sc_correction_range_start+sc_starts.optInt(0);
                span_end = sc_correction_range_start+sc_ends.optInt(0);
                highlightStringInRange(span_begin, span_end);
            } else {
                sc_starts = null;
                sc_ends = null;
            }
        }
    }

    private boolean offsetWithinRange(int offset, float x){
        Layout layout = noteContents.getLayout();
        try {
            float offsetx = layout.getPrimaryHorizontal(offset);
            if (offsetx < x - 250 || offsetx > x + 250) {
                return false;
            }
            return true;
        }
        catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    /**
     * compute text and related pixel value
     */
    private float getContentHeight() {
        return noteContents.getLayout().getHeight();
    }

    private float getLastLineWidth(int startidx, int endidx) {
//        Log.e(TAG, "start "+startidx+" end " + endidx );

        int lines =  noteContents.getLineCount();
        for (int line = lines-1; line >= 0; line--) {
            int end = noteContents.getLayout().getLineEnd(line);
            int start = noteContents.getLayout().getLineStart(line);
//            Log.e(TAG, "line " + line + " start "+start+" end " + end);
            if (start <= startidx && end >= endidx)
                return noteContents.getLayout().getLineWidth(line);
        }
        return 0;
    }

    private float getWordWidth(String word) {
        return noteContents.getPaint().measureText(word);
    }

    private float getLineHeight() {
        return noteContents.getLayout().getHeight()/noteContents.getLineCount();
    }

    private int getTextIndexOfXY(float x, float y, int yoffset) {
        Layout layout = noteContents.getLayout();
        y = Math.max(y-yoffset, 0); //offset
        int line = layout.getLineForVertical((int)y);
        int offset = layout.getOffsetForHorizontal( line,  x);
        return offset;
    }

    private String getSurroudningTextOfIndex(String content, int index) {
        if (index >= content.length()){
            return null;
        }

        int startidx = Math.max(0, index-30);
        int endidx = content.lastIndexOf(correction);

        if (endidx <= 0) {
            endidx = Math.min(content.length() - 1, index + 30);
        }
        else {
            endidx = Math.min(endidx, index+30);
        }


        int newline = content.substring(startidx, index).lastIndexOf('\n');
        if (newline > -1){
            startidx += newline+1;
        }
        else if (startidx > 0) {
            for (int i = startidx; i < index; i++) {
                char c = content.charAt(i);
                if (!Character.isLetter(c) && !Character.isDigit(c)) {
                    startidx = i;
                    break;
                }
            }
            startidx += 1;
        }
//
//        newline = content.substring(endidx).indexOf('\n');
//        if (newline > -1){
//            endidx += newline;
//        }
        //even it equals the end of content length, we need to go back
        //because content length is the last correction
//        else {
            for (int i = endidx; i > index; i--) {
                char c = content.charAt(i);
                if (!Character.isLetter(c) && !Character.isDigit(c)) {
                    endidx = i;
                    break;
                }
            }
//        }

        if (endidx <= startidx){
            return null;
        }

        sent_begin = startidx;
        sent_end = endidx+1;
        return content.substring(startidx, endidx+1);
    }

    /** animation and string effects
     * for plain method
      */
    private void replaceWithAnimation() {
        String content = noteContents.getText().toString();

        last_content = content;
        last_span_begin = span_begin;
        last_span_end = span_end;
        if (span_end == span_begin){
            last_span_end += 1;
        }

        int lastidx = content.lastIndexOf(correction);
        if (lastidx == -1){
            last_content = null;
            return;
        }

        content = content.substring(0, lastidx);
        if (span_end >= content.length()){
            last_content = null;
            return;
        }

        String newcontent = content.substring(0, span_begin)+correction+content.substring(span_end);
        //for insertion
        if (span_begin == span_end){
            if (correct_option.equals("smart") ){
                newcontent = content.substring(0, span_begin) + correction + " " + content.substring(span_end);
            } else {
                newcontent = content.substring(0, span_begin) + " " + correction + content.substring(span_end);
                span_begin += 1;
            }
        }
        correct_content = newcontent;
        span_end = span_begin+correction.length();
        //create a tmp value incase the internal attributes get changed during animation
        int sbegin = span_begin;
        int send = span_end;
        ValueAnimator valueAnimator = ValueAnimator.ofArgb(0xffff6600,0xff000000);
        SpannableStringBuilder sban = new SpannableStringBuilder(newcontent);
        valueAnimator.addUpdateListener((ValueAnimator animation) -> {
                sban.setSpan(new ForegroundColorSpan((Integer)animation.getAnimatedValue()), sbegin, send, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                noteContents.setText(sban);
                noteContents.setSelection(noteContents.getText().length());
        });

        valueAnimator.setDuration(400);
        valueAnimator.start();
    }

    /** animation and string effects
     * for smart method (throw, magic)
     */
    private void replaceWithAnimationInRange(String text, int start, int len){
        correct_content = text;
        ValueAnimator valueAnimator = ValueAnimator.ofArgb(0xffff6600,0xff000000);
        SpannableStringBuilder sban = new SpannableStringBuilder(text);
        valueAnimator.addUpdateListener((ValueAnimator animation) -> {
            sban.setSpan(new ForegroundColorSpan((Integer)animation.getAnimatedValue()), start, start+len, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            noteContents.setText(sban);
            noteContents.setSelection(noteContents.getText().length());
        });

        valueAnimator.setDuration(500);
        valueAnimator.start();
    }

    private void undoWithAnimation(){
        if (last_content == null) return;

        ValueAnimator valueAnimator = ValueAnimator.ofArgb(0xff00ff00,0xff000000);
        SpannableStringBuilder sban = new SpannableStringBuilder(last_content);

        if (last_span_end < last_span_begin) {
            last_span_end = last_span_begin+1;
            for (int i = last_span_begin; i < last_content.length(); ++i) {
                char c = last_content.charAt(i);
                if (!Character.isLetterOrDigit(c)) {
                    last_span_end = i;
                    break;
                }
            }
        }
        last_content = null;
        correct_content = null;

        int tmpbegin = last_span_begin;
        int tmpend = last_span_end;
        valueAnimator.addUpdateListener((ValueAnimator animation) -> {
            sban.setSpan(new ForegroundColorSpan((Integer)animation.getAnimatedValue()), tmpbegin, tmpend, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            noteContents.setText(sban);
            noteContents.setSelection(noteContents.getText().length());
        });

        valueAnimator.setDuration(400);
        valueAnimator.start();
    }

    private void setTextWithoutWatcher(SpannableStringBuilder text) {
        if (correct_option.equals("smart") || testingmode == true) {
            noteContents.removeTextChangedListener(this);
            noteContents.setText(text);
            noteContents.addTextChangedListener(this);
        } else {
            noteContents.setText(text);
        }
    }

    //use magnifier if the android version is above P
    // background works horrible...
    //https://medium.com/google-developer-experts/exploring-android-p-magnifier-ddfd06bdecbe
    //return : false: don't change true: change
    private boolean getHighlightStringOnIndex(String content, int index) {

        if (index >= content.length()) {
            span_begin = -1;
            span_end = -1;
            return false;
        }

        char c = content.charAt(index);
        //for insert
        if (c == ' '){
            span_begin = index;
            span_end = index;
            sb.clear();
            sb.clearSpans();
            sb.append(content);
            // Set the text color for first 4 characters
            sb.setSpan(bcs, span_begin, span_end+1, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            return true;
        }

        if (!Character.isLetter(c) && !Character.isDigit(c)) {
            //if it's not space but there's character before , then maybe the user wants to replace , but the word is too short to select
            if (c > 0 && Character.isLetter(c-1)){
                return false;
            }
            span_begin = -1;
            span_end = -1;
            return false;
        }

        int tmp_span_begin = index;
        int tmp_span_end = index;

        for (int i = index - 1; i >= 0; i--) {
            c = content.charAt(i);
            if (!Character.isLetter(c) && !Character.isDigit(c)) {
                tmp_span_begin = i + 1;
                break;
            }
        }

        for (int i = index + 1; i < content.length(); i++) {
            c = content.charAt(i);
            if (!Character.isLetter(c) && !Character.isDigit(c)) {
                tmp_span_end = i;
                break;
            }
        }
        if (tmp_span_begin == -1) tmp_span_begin = 0;
        if (tmp_span_end == -1) tmp_span_end = content.length();

        //we don't highlight the correction itself
        if (content.lastIndexOf(correction) == tmp_span_begin){
            span_end = -1;
            span_begin = -1;
            return false;
        }

        //the higlight span doens't change
        if (span_begin == tmp_span_begin && span_end == tmp_span_end)
            return false;

        sb.clear();
        sb.clearSpans();
        sb.append(content);
        span_begin = tmp_span_begin;
        span_end = tmp_span_end;
        // Set the text color for first 4 characters
        sb.setSpan(bcs, span_begin, span_end, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        return true;
    }

    private void highlightStringInRange(int sbegin, int send) {
        if (span_begin > span_end || sbegin < 0) return;
        String content = noteContents.getText().toString();
        if (sc_original_content != null){
            content = sc_original_content;
        }
        //insert, we highlight the space (by adding more !)
        if (sbegin == send){
            send += 2;
            content = content.substring(0, sbegin) + "  " + content.substring(sbegin);
        }
        sb.clear();
        sb.clearSpans();
        sb.append(content);
        sb.setSpan(bcs, sbegin,
                send, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        setTextWithoutWatcher(sb);
    }

    private void cancelSmartHighlight() {
//        Log.e(TAG, "smart canceled by user!");
        smart_correction_begin = false;
        sb.clear();
        sb.clearSpans();
        sb.append(sc_original_content);
        setTextWithoutWatcher(sb);
    }

    /**
     * Float button interactions
     */
    private class FloatButtonReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(FloatingButtonService.FLOAT_BUTTON_INTENT)) {
            }}
    }

    private void releaseVelocityTracker() { if (null != mVelocityTracker) { mVelocityTracker.clear(); mVelocityTracker.recycle(); mVelocityTracker = null; } }

    /**
     * Note related Functions
     */
    private void deleteNote(String filename) {
        // Build the pathname to delete file, then perform delete operation
        File fileToDelete = new File(getActivity().getFilesDir() + File.separator + filename);
        fileToDelete.delete();
    }

    // Saves notes to /data/data/com.farmerbb.notepad/files
    private void saveNote() throws IOException {
        // Set current note contents to a String
        noteContents = getActivity().findViewById(R.id.editText1);
        contents = noteContents.getText().toString();

        // Write the String to a new file with filename of current milliseconds of Unix time
        if(contents.equals("") && filename.equals("draft"))
            finish(null);
        else {

            // Set a new filename if this is not a draft
            String newFilename;
            if(filename.equals("draft") || filename.equals("exported_note"))
                newFilename = filename;
            else
                newFilename = String.valueOf(System.currentTimeMillis());

            // Write note to disk
            FileOutputStream output = getActivity().openFileOutput(newFilename, Context.MODE_PRIVATE);
            output.write(contents.getBytes());
            output.close();

            // Delete old file
            if(!(filename.equals("draft") || filename.equals("exported_note")))
                deleteNote(filename);

            // Show toast notification
            if(filename.equals("draft"))
                showToast(R.string.draft_saved);
            else if(!filename.equals("exported_note"))
                showToast(R.string.note_saved);

            // Old file is no more
            if(!(filename.equals("draft") || filename.equals("exported_note"))) {
                filename = newFilename;
                contentsOnLoad = contents;
                length = contentsOnLoad.length();
            }

            // Send broadcast to MainActivity to refresh list of notes
            Intent listNotesIntent = new Intent();
            listNotesIntent.setAction("com.farmerbb.notepad.LIST_NOTES");
            LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(listNotesIntent);
        }
    }

    // Method used to generate toast notifications
    private void showToast(int message) {
        Toast toast = Toast.makeText(getActivity(), getResources().getString(message), Toast.LENGTH_SHORT);
        toast.show();
    }

    public void onBackDialogNegativeClick(String filename) {
        // User touched the dialog's negative button
        showToast(R.string.changes_discarded);
        finish(filename);
    }

    public void onBackDialogPositiveClick(String filename) {
        // User touched the dialog's positive button
        try {
            saveNote();
            finish(filename);
        } catch (IOException e) {
            // Show error message as toast if file fails to save
            showToast(R.string.failed_to_save);
        }
    }

    public void onDeleteDialogPositiveClick() {
        // User touched the dialog's positive button
        deleteNote(filename);
        showToast(R.string.note_deleted);

        if(getActivity().getComponentName().getClassName().equals("com.farmerbb.notepad.activity.MainActivity")
                && getActivity().findViewById(R.id.layoutMain).getTag().equals("main-layout-large")) {
            // Send broadcast to NoteListFragment to refresh list of notes
            Intent listNotesIntent = new Intent();
            listNotesIntent.setAction("com.farmerbb.notepad.LIST_NOTES");
            LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(listNotesIntent);
        }

        finish(null);
    }

    public void onSaveDialogNegativeClick() {
        // User touched the dialog's negative button
        if(isSavedNote) {
            showToast(R.string.changes_discarded);

            Bundle bundle = new Bundle();
            bundle.putString("filename", filename);

            Fragment fragment = new NoteViewFragment();
            fragment.setArguments(bundle);

            getFragmentManager()
                    .beginTransaction()
                    .replace(R.id.noteViewEdit, fragment, "NoteViewFragment")
                    .commit();
        } else {
            showToast(R.string.changes_discarded);
            finish(null);
        }
    }

    public void onSaveDialogPositiveClick() {
        // User touched the dialog's positive button
        try {
            saveNote();

            if(listener.isShareIntent())
                finish(null);
            else {
                Bundle bundle = new Bundle();
                bundle.putString("filename", filename);

                Fragment fragment = new NoteViewFragment();
                fragment.setArguments(bundle);

                getFragmentManager()
                        .beginTransaction()
                        .replace(R.id.noteViewEdit, fragment, "NoteViewFragment")
                        .commit();
            }
        } catch (IOException e) {
            // Show error message as toast if file fails to save
            showToast(R.string.failed_to_save);
        }
    }

    public void onBackPressed(String filename) {
        // Pop back stack if no changes were made since last save
        if(contentsOnLoad.equals(noteContents.getText().toString())) {
            finish(filename);
        } else {
            // Finish if EditText is empty
            if(noteContents.getText().toString().isEmpty())
                finish(filename);
            else {
                SharedPreferences pref = getActivity().getSharedPreferences(getActivity().getPackageName() + "_preferences", Context.MODE_PRIVATE);
                if(pref.getBoolean("show_dialogs", true)) {
                    // Show back button dialog
                    listener.showBackButtonDialog(filename);
                } else {
                    try {
                        saveNote();
                        finish(filename);
                    } catch (IOException e) {
                        // Show error message as toast if file fails to save
                        showToast(R.string.failed_to_save);
                    }
                }
            }
        }
    }

    public void dispatchKeyShortcutEvent(int keyCode) {
        switch(keyCode) {

                // CTRL+S: Save
            case KeyEvent.KEYCODE_S:
                // Set current note contents to a String
                contents = noteContents.getText().toString();

                // If EditText is empty, show toast informing user to enter some text
                if(contents.equals(""))
                    showToast(R.string.empty_note);
                else {
                    try {
                        // Keyboard shortcut just saves the note; no dialog shown
                        saveNote();
                        isSavedNote = true;

                        // Change window title
                        String title;
                        try {
                            title = listener.loadNoteTitle(filename);
                        } catch (IOException e) {
                            title = getResources().getString(R.string.edit_note);
                        }

                        getActivity().setTitle(title);

                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            Bitmap bitmap = ((BitmapDrawable) ContextCompat.getDrawable(getActivity(), R.drawable.ic_recents_logo)).getBitmap();

                            ActivityManager.TaskDescription taskDescription = new ActivityManager.TaskDescription(title, bitmap, ContextCompat.getColor(getActivity(), R.color.primary));
                            getActivity().setTaskDescription(taskDescription);
                        }
                    } catch (IOException e) {
                        // Show error message as toast if file fails to save
                        showToast(R.string.failed_to_save);
                    }
                }
                break;

                // CTRL+D: Delete
            case KeyEvent.KEYCODE_D:
                listener.showDeleteDialog();
                break;

                // CTRL+H: Share
            case KeyEvent.KEYCODE_H:
                // Set current note contents to a String
                contents = noteContents.getText().toString();

                // If EditText is empty, show toast informing user to enter some text
                if(contents.equals(""))
                    showToast(R.string.empty_note);
                else {
                    // Send a share intent
                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.putExtra(Intent.EXTRA_TEXT, contents);
                    shareIntent.setType("text/plain");

                    // Verify that the intent will resolve to an activity, and send
                    if(shareIntent.resolveActivity(getActivity().getPackageManager()) != null)
                        startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.send_to)));
                }
                break;
        }
    }

    private void finish(String filename) {
        if(listener.isShareIntent())
            getActivity().finish();
        else if(filename == null) {
            // Add NoteListFragment or WelcomeFragment
            Fragment fragment;
            if(getActivity().findViewById(R.id.layoutMain).getTag().equals("main-layout-normal"))
                fragment = new NoteListFragment();
            else
                fragment = new WelcomeFragment();

            getFragmentManager()
                .beginTransaction()
                .replace(R.id.noteViewEdit, fragment, "NoteListFragment")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit();
        } else {
            Bundle bundle = new Bundle();
            bundle.putString("filename", filename);

            Fragment fragment;
            String tag;

            if(directEdit) {
                fragment = new NoteEditFragment();
                tag = "NoteEditFragment";
            } else {
                fragment = new NoteViewFragment();
                tag = "NoteViewFragment";
            }

            fragment.setArguments(bundle);

            // Add NoteViewFragment or NoteEditFragment
            getFragmentManager()
                    .beginTransaction()
                    .replace(R.id.noteViewEdit, fragment, tag)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                    .commit();
        }
    }

    public void switchNotes(String filename) {
        // Hide soft keyboard
        EditText editText = getActivity().findViewById(R.id.editText1);
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);

        // Act as if the back button was pressed
        onBackPressed(filename);
    }

    public String getFilename() {
        return filename;
    }

    private void prepareTestingPhrases() {
        if (!testingmode) return;
        try {
            InputStream is = getContext().getResources().openRawResource(R.raw.phrases);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line = reader.readLine();
            String[] arr = {"", "ready", ""};
            teststrings.add(arr);
            while (line != null) {
                arr = line.split("\\t");
                arr[0] = "\n\n\n\n\n\n\n\n\n\n"+arr[0].trim()+" ";
                arr[1] = "\n\n\n\n\n\n\n\n\n\n"+arr[1].trim();
                arr[2] = arr[2].trim();
                teststrings.add(arr);
                line = reader.readLine();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
