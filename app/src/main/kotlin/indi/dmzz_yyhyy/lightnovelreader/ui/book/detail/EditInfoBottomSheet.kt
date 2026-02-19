package indi.dmzz_yyhyy.lightnovelreader.ui.book.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import indi.dmzz_yyhyy.lightnovelreader.R
import indi.dmzz_yyhyy.lightnovelreader.ui.components.SwitchChip
import io.nightfish.lightnovelreader.api.book.BookInformation
import io.nightfish.lightnovelreader.api.book.BookVolumes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditInfoBottomSheet(
    sheetState: SheetState,
    bookInformation: BookInformation,
    onDismissRequest: () -> Unit,
    onClickSave: (BookInformation) -> Unit
) {
    val new = bookInformation.toMutable()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        tonalElevation = 16.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.export_epub),
                    style = typography.displayMedium
                )
                Spacer(Modifier.width(16.dp))
                Button(onClick = {
                    onClickSave(bookInformation)
                    onDismissRequest()
                }) {
                    Text(stringResource(R.string.export), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}