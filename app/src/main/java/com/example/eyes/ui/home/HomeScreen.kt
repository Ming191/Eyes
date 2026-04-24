package com.example.eyes.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.eyes.ui.navigation.Routes
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = koinViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.greet()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BigActionButton(
            label = "Xem xung quanh",
            description = "Xem xung quanh. Nhấn để mở camera nhận dạng vật cản và đọc văn bản",
            modifier = Modifier.weight(1f),
            onClick = { navController.navigate(Routes.Camera) }
        )

        BigActionButton(
            label = "Đọc văn bản",
            description = "Đọc văn bản. Nhấn để mở chế độ đọc OCR",
            modifier = Modifier.weight(1f),
            onClick = { navController.navigate(Routes.Camera) }
        )

        BigActionButton(
            label = "Đi đến nơi",
            description = "Đi đến nơi. Nhấn để mở bản đồ và dẫn đường",
            modifier = Modifier.weight(1f),
            onClick = { navController.navigate(Routes.Map) }
        )
    }
}

@Composable
private fun BigActionButton(
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .heightIn(min = 88.dp)
            .semantics { contentDescription = description },
        colors = ButtonDefaults.buttonColors()
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold
        )
    }
}
