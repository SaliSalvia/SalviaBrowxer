# Library consumer rules cover AndroidX, Room, Hilt, Compose, OkHttp and WorkManager. Keep this
# file deliberately narrow so R8 can optimize and shrink the release build effectively.

# Download state names are persisted by Room via DownloadState.name/valueOf.
-keepclassmembers enum com.salvia.salviabrowxer.core.model.DownloadState {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
