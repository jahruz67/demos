package dev.demos.graphing.data

import android.content.Context

fun GraphRepository(context: Context): GraphRepository = GraphRepository(context.noBackupFilesDir)
