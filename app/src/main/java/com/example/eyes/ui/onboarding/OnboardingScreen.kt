package com.example.eyes.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.eyes.ui.permission.PermissionScreen

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Màn hình onboarding" }
    ) { page ->
        when (page) {
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Bước 1: Cấp quyền",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    PermissionScreen(modifier = Modifier.fillMaxWidth())
                }
            }

            1 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .semantics { contentDescription = "Trang hướng dẫn cử chỉ" },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Bước 2: Hướng dẫn cử chỉ",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Vuốt ngang để đổi màn hình, chạm đúp để chọn chức năng.",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            2 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .semantics { contentDescription = "Trang bắt đầu ứng dụng" },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Bước 3: Sẵn sàng",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Button(
                        onClick = onFinish,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .semantics { contentDescription = "Nút bắt đầu sử dụng ứng dụng" }
                    ) {
                        Text("Bắt đầu")
                    }
                }
            }
        }
    }
}
