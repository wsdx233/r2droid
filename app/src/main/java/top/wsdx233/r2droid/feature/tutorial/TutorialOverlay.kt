package top.wsdx233.r2droid.feature.tutorial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.R

@Composable
fun TutorialOverlay(
    state: OnboardingTutorial.State,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val step = OnboardingTutorial.steps.getOrNull(state.stepIndex) ?: return
    val total = OnboardingTutorial.steps.size
    val isLast = state.stepIndex == total - 1
    val alignTop = step.target.showCommandSheet

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = if (alignTop) Alignment.TopCenter else Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(
                    top = if (alignTop) 24.dp else 0.dp,
                    bottom = if (alignTop) 0.dp else 96.dp
                ),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tutorial_progress, state.stepIndex + 1, total),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.tutorial_skip))
                    }
                }

                Text(
                    text = stringResource(step.titleRes),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = stringResource(step.bodyRes),
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onPrevious,
                        enabled = state.stepIndex > 0
                    ) {
                        Text(stringResource(R.string.tutorial_previous))
                    }
                    TextButton(onClick = onNext) {
                        Text(stringResource(if (isLast) R.string.tutorial_done else R.string.tutorial_next))
                    }
                }
            }
        }
    }
}
