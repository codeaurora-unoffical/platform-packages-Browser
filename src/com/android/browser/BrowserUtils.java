/**
*(1) warring the max length of the input String for EditText,
*(2)  check if the url match the patten ACCEPTED_URI_SCHEMA
*/

package com.android.browser;
import java.util.regex.*;

import android.content.Context;
import android.text.InputFilter;
import android.text.Spanned;
import android.widget.EditText;
import android.app.AlertDialog;
import android.content.DialogInterface;

public class BrowserUtils {
    public static final Pattern ACCEPTED_URI_SCHEMA = Pattern.compile(
             "(?i)" + // switch on case insensitive matching
             "(" +    // begin group for schema
             "(?:http|https|file):\\/\\/" +
             "|(?:inline|data|about|javascript):" +
             "|(?:rtsp|rtspu|rtspt):\\/\\/" +
             "|(?:wtai):\\/\\/" +
             "|(?:tel):" +
             ")" +
             "(.*)" );

    BrowserUtils(){
        super();
    } 
    public static final int filenameMaxLength = 32;
    public static final int addressMaxLength = 2048;
    public static void lengthFilter( final EditText editText, 
                                     final int max_length, final String err_msg) {

        InputFilter[] filters = new InputFilter[1];
        filters[0] = new InputFilter.LengthFilter(max_length) {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                int destLen =dest.toString().length(); 
                int sourceLen =  source.toString().length();
                int keep = max_length - (destLen - (dend - dstart));
                if (keep <= 0) {
                    editText.setError(err_msg);
                    return "";
                } else if (keep >= end - start) {
                    return null; // keep original
                } else {
                    editText.setError(err_msg);
                    return source.subSequence(start, start + keep);
                }}};
        editText.setFilters(filters);
    }   
    public static void maxLengthFilter( Context context, final EditText editText, 
                                     final int max_length) {

        final String err_msg = context.getString(R.string.browser_max_input, max_length);
        InputFilter[] filters = new InputFilter[1];
        filters[0] = new InputFilter.LengthFilter(max_length) {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                int destLen =dest.toString().length(); 
                int sourceLen =  source.toString().length();
                int keep = max_length - (destLen - (dend - dstart));
                if (keep <= 0) {
                    editText.setError(err_msg);
                    return "";
                } else if (keep >= end - start) {
                    return null; // keep original
                } else {
                    editText.setError(err_msg);
                    return source.subSequence(start, start + keep);
                }}};
        editText.setFilters(filters);
    }
    // check if the url match the patten ACCEPTED_URI_SCHEMA
    public static boolean matchUrl(String url) {
        final String urlTemp = url;
        Matcher matcher = ACCEPTED_URI_SCHEMA.matcher(urlTemp);
        if (matcher.matches()) {
            return true;
        } else {
            return false;
        }
    }

    public static void lengthFilter2( final Context context, final EditText editText, 
                                      final int max_length) {
        InputFilter[] contentFilters = new InputFilter[1];
        contentFilters[0] = new InputFilter.LengthFilter(max_length) {
            public CharSequence filter(CharSequence source, int start, int end,
                                       Spanned dest, int dstart, int dend){

                int keep = max_length - (dest.length() - (dend - dstart));
                if (keep <= 0) {
                    showWarningDialog(context, max_length);
                    return "";
                } else if (keep >= end - start) {
                    return null;
                } else {
                    if (keep < source.length()) {
                        showWarningDialog(context, max_length);
                    }
                    return source.subSequence(start, start + keep);
                }
            }
        };
        editText.setFilters(contentFilters);
    }

    private static void showWarningDialog(final Context context, int max_length) {

         new AlertDialog.Builder(context)
            .setTitle(R.string.browser_max_input_title)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setMessage(context.getString(R.string.browser_max_input, max_length))
            .setPositiveButton(R.string.ok, 
                new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    return;
                }
                })
            .show();
    	
    }

}
