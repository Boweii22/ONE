package com.oneglobal.billboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

fun suggestedCountry(): String = Locale.getDefault().country.uppercase(Locale.ROOT)
    .takeIf { it in Locale.getISOCountries() }.orEmpty()

fun countryLabel(code: String): String {
    if (code !in Locale.getISOCountries()) return "Country not shared"
    val flag = code.map { String(Character.toChars(0x1F1E6 + it.code - 'A'.code)) }.joinToString("")
    return "$flag " + Locale("", code).getDisplayCountry(Locale.getDefault())
}

@Composable
fun CountryPicker(code: String, onChange: (String) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    var lastCountry by remember { mutableStateOf(code.ifBlank { suggestedCountry() }) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SHOW MY COUNTRY", modifier = Modifier.weight(1f).padding(top = 12.dp))
            Switch(checked = code.isNotBlank(), onCheckedChange = {
                if (!it) { lastCountry = code; onChange("") }
                else if (lastCountry.isNotBlank()) onChange(lastCountry) else choosing = true
            })
        }
        TextButton(onClick = { choosing = true }) {
            Text(if (code.isBlank()) "Choose country" else countryLabel(code) + " · Change")
        }
        Text("Suggested from your device region, not your physical location. Country only. No GPS.")
    }
    if (choosing) AlertDialog(
        onDismissRequest = { choosing = false },
        title = { Text("Choose your country") },
        text = {
            val countries = remember { Locale.getISOCountries().sortedBy { Locale("", it).getDisplayCountry(Locale.getDefault()) } }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(countries, key = { it }) { country ->
                    Text(countryLabel(country), Modifier.fillMaxWidth().clickable {
                        lastCountry = country; onChange(country); choosing = false
                    }.padding(vertical = 15.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } },
    )
}
