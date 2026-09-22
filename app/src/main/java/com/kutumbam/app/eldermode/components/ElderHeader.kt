package com.kutumbam.app.eldermode.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.eldermode.models.ElderStrings
import com.kutumbam.app.speech.AppLanguage
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons

/**
 * Top-level header for Elder Mode.
 * Provides accessible back/exit action, 1-tap language switcher,
 * and high-contrast personal greeting.
 */
@Composable
fun ElderHeader(
    memberName: String,
    relation: String,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    backButtonLabel: String = "Back",
    showGreeting: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Top Navigation Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 56dp Back / Standard View button
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(K.Card)
                    .border(1.dp, K.Border, CircleShape)
                    .clickable(role = Role.Button, onClick = onBackClick)
                    .semantics {
                        this.role = Role.Button
                        this.contentDescription = backButtonLabel
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KIcons.Back,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = K.Ink,
                )
            }

            // Language Switcher Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppLanguage.entries.forEach { langOption ->
                    val selected = langOption == language
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) K.Teal else K.Card)
                            .border(1.dp, if (selected) K.Teal else K.Border, RoundedCornerShape(20.dp))
                            .clickable(
                                role = Role.RadioButton,
                                onClick = { onLanguageChange(langOption) },
                            )
                            .semantics {
                                this.role = Role.RadioButton
                                this.contentDescription = "Switch language to ${langOption.label}"
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = langOption.label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else K.Ink,
                        )
                    }
                }
            }
        }

        // Personalized Warm Greeting
        if (showGreeting && memberName.isNotBlank()) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = ElderStrings.greeting(language, memberName),
                    fontFamily = K.Display,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = K.Ink,
                )
                if (relation.isNotBlank()) {
                    Text(
                        text = relation,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = K.Muted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
