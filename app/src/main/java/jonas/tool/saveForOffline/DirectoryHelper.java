package jonas.tool.saveForOffline;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DirectoryHelper {

    public static String createUniqueFilename() {
        //creates filenames based on the date and time, hopefully unique
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
        return sdf.format(new Date());
    }

    private static File getStorageDir(Context context) {
        // Use app-specific storage directory which requires no special permissions.
        File directory = context.getExternalFilesDir(null);
        if (directory != null && !directory.exists()) {
            if (!directory.mkdirs()) {
                Log.e("DirectoryHelper", "Could not create directory " + directory.getAbsolutePath());
            }
        }
        return directory;
    }

    public static String getDestinationDirectory(Context context) {
        File storageDir = getStorageDir(context);
        if (storageDir == null) {
            Log.e("DirectoryHelper", "External storage not available");
            // Fallback or error handling
            return null;
        }

        File destination = new File(storageDir, createUniqueFilename());
        if (!destination.exists() && !destination.mkdirs()) {
            Log.e("DirectoryHelper", "Could not create destination directory " + destination.getAbsolutePath());
        }

        // Create .nomedia file in the root storage directory if it doesn't exist.
        createNomediaFile(storageDir);

        return destination.getAbsolutePath() + File.separator;
    }

    private static void createNomediaFile(File storageDir) {
        if (storageDir == null) return;
        File noMedia = new File(storageDir, ".nomedia");
        if (!noMedia.exists()) {
            try {
                if(noMedia.createNewFile()){
                    Log.d("DirectoryHelper", ".nomedia file created");
                }
            } catch (IOException e) {
                Log.e("DirectoryHelper", "IOException while creating .nomedia file in " + storageDir.getAbsolutePath(), e);
            }
        }
    }

    public static void deleteDirectory(File directory) {
        if (directory == null || !directory.exists()) {
            return;
        }

        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteDirectory(f);
                }
            }
        }
        if(!directory.delete()){
            Log.e("DirectoryHelper", "Could not delete directory " + directory.getAbsolutePath());
        }
    }
}