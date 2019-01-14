package com.farmerbb.notepad.util;

import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ExpLogger {
    private int current_task_idx;
    private int undo_times;
    private int swipe_left;
    private int swipe_right;
    private long task_begin_time;
    private JSONArray log_array;
    private JSONObject log_task;
    private JSONArray text_change_array;

    public void logStart() {
        current_task_idx = 0;
        undo_times = 0;
        log_array = new JSONArray();
    }

    public void logEnd() {
        try {
            Writer output = null;
            File file = new File(getFiledirForNow());
            output = new BufferedWriter(new FileWriter(file));
            output.write(log_array.toString());
            output.close();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void startTask(int index) {
        current_task_idx = index;
        undo_times = 0;
        task_begin_time = System.nanoTime();
        log_task = new JSONObject();
        text_change_array = new JSONArray();
        try {
            log_task.put("starttime", task_begin_time);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void finishTask() {
        try {
            log_task.put("undo", undo_times);
            log_task.put("swipe_left", swipe_left);
            log_task.put("swipe_right", swipe_right);
            log_task.put("tottime", (System.nanoTime()-task_begin_time)/1000000);
            log_task.put("text_changes", text_change_array);
            log_array.put(log_task);
            log_task = new JSONObject();
            text_change_array = new JSONArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void logUndo() {
        undo_times += 1;
    }

    public void logChange(String text) {
        if (text == null || text.isEmpty()) return;
        try {
            JSONObject change = new JSONObject();
            change.put("text", text);
            change.put("time", System.nanoTime());
            text_change_array.put(change);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void logSwipe(String direction) {
        if (direction.equals("left")){
            swipe_left += 1;}
        else {
            swipe_right += 1;
        }
    }

    String getFiledirForNow() {
        File dir = new File(Environment.getExternalStorageDirectory() + "/Download/correctionExp/");
        if (!dir.exists())
            dir.mkdirs();
        String fname = new SimpleDateFormat("MM_dd_HH_mm'.json'").format(new Date());
        fname = Environment.getExternalStorageDirectory()+"/Download/correctionExp/Exp_"+fname;
        return fname;
    }

}
