package com.dzirbel.kotify.ui.panel.debug

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.dzirbel.kotify.Logger
import com.dzirbel.kotify.ui.components.CheckboxWithLabel
import com.dzirbel.kotify.ui.theme.Dimens
import com.dzirbel.kotify.ui.theme.LocalColors
import com.dzirbel.kotify.ui.util.mutate

private data class DatabaseSettings(
    val groupByTransaction: Boolean = true,
)

private val databaseSettings = mutableStateOf(DatabaseSettings())

@Composable
fun DatabaseTab(scrollState: ScrollState) {
    val groupByTransaction = databaseSettings.value.groupByTransaction
    Column(Modifier.fillMaxWidth().background(LocalColors.current.surface3).padding(Dimens.space3)) {
        CheckboxWithLabel(
            modifier = Modifier.fillMaxWidth(),
            checked = groupByTransaction,
            onCheckedChange = { databaseSettings.mutate { copy(groupByTransaction = it) } },
            label = { Text("Group by transaction") },
        )
    }

    EventList(log = Logger.Database, key = Unit, scrollState = scrollState) { event ->
        when (event.data) {
            Logger.Database.EventType.STATEMENT -> !groupByTransaction
            Logger.Database.EventType.TRANSACTION -> groupByTransaction
        }
    }
}