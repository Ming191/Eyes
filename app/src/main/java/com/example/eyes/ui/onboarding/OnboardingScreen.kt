package com.example.eyes.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.eyes.ui.permission.PermissionScreen
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val summary: String,
    val accessibilityLabel: String
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val pages = listOf(
        OnboardingPage(
            title = "Cấp quyền thiết yếu",
            summary = "Camera, micro và vị trí giúp ứng dụng mô tả lối đi, đọc văn bản và chuẩn bị dẫn đường.",
            accessibilityLabel = "Bước một. Cấp quyền thiết yếu cho camera, micro và vị trí."
        ),
        OnboardingPage(
            title = "Làm quen với thao tác",
            summary = "Vuốt ngang để đổi màn hình, chạm đúp để chọn, và chờ thông báo rung trước khi di chuyển tiếp.",
            accessibilityLabel = "Bước hai. Làm quen với thao tác vuốt ngang, chạm đúp và phản hồi rung."
        ),
        OnboardingPage(
            title = "Sẵn sàng bắt đầu",
            summary = "Trang chủ sẽ đưa bạn tới camera, bản đồ và phần chỉnh tốc độ đọc chỉ bằng vài thao tác lớn, rõ ràng.",
            accessibilityLabel = "Bước ba. Sẵn sàng bắt đầu với camera, bản đồ và cài đặt phản hồi."
        )
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .semantics { contentDescription = "Màn hình giới thiệu và thiết lập ban đầu" },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Giới thiệu SoundVision. Trợ lý bằng giọng nói dành cho người dùng khiếm thị."
                },
            shape = MaterialTheme.shapes.large,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Thiết lập nhanh",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Chuẩn bị SoundVision theo từng bước ngắn gọn",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Mỗi bước đều dùng nút lớn, nhãn rõ ràng và mô tả bằng tiếng Việt cho TalkBack.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            val item = pages[page]
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = item.accessibilityLabel },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Bước ${page + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (page == 0) {
                        PermissionScreen(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                    }
                },
                enabled = pagerState.currentPage > 0,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = "Nút quay lại bước trước" }
            ) {
                Text("Quay lại")
            }

            Button(
                onClick = {
                    if (pagerState.currentPage == pages.lastIndex) {
                        onFinish()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 88.dp)
                    .semantics {
                        contentDescription = if (pagerState.currentPage == pages.lastIndex) {
                            "Nút bắt đầu sử dụng ứng dụng"
                        } else {
                            "Nút sang bước tiếp theo"
                        }
                    },
                colors = ButtonDefaults.buttonColors()
            ) {
                Text(if (pagerState.currentPage == pages.lastIndex) "Bắt đầu" else "Tiếp tục")
            }
        }
    }
}
