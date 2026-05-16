package com.watchtogether.data.model

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

data class VideoItem(
    val id: Long,
    val title: String,
    val uri: Uri,
    val path: String,
    val duration: Long,
    val size: Long,
    val mimeType: String,
    val dateAdded: Long,
    val folderName: String,
    val thumbnailUri: Uri? = null
) : Parcelable {

    val formattedDuration: String
        get() {
            val totalSeconds = duration / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val formattedSize: String
        get() {
            val kb = size / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.1f GB", gb)
                mb >= 1.0 -> String.format("%.1f MB", mb)
                else -> String.format("%.0f KB", kb)
            }
        }

    constructor(parcel: Parcel) : this(
        id = parcel.readLong(),
        title = parcel.readString() ?: "",
        uri = parcel.readParcelable(Uri::class.java.classLoader) ?: Uri.EMPTY,
        path = parcel.readString() ?: "",
        duration = parcel.readLong(),
        size = parcel.readLong(),
        mimeType = parcel.readString() ?: "",
        dateAdded = parcel.readLong(),
        folderName = parcel.readString() ?: "",
        thumbnailUri = parcel.readParcelable(Uri::class.java.classLoader)
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(title)
        parcel.writeParcelable(uri, flags)
        parcel.writeString(path)
        parcel.writeLong(duration)
        parcel.writeLong(size)
        parcel.writeString(mimeType)
        parcel.writeLong(dateAdded)
        parcel.writeString(folderName)
        parcel.writeParcelable(thumbnailUri, flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<VideoItem> {
        override fun createFromParcel(parcel: Parcel): VideoItem = VideoItem(parcel)
        override fun newArray(size: Int): Array<VideoItem?> = arrayOfNulls(size)
    }
}
