import 'package:flutter/material.dart';

class DiscordColors {
  static const railBackground = Color(0xFF1E1F22);
  static const sidebarBackground = Color(0xFF2B2D31);
  static const chatBackground = Color(0xFF313338);
  static const inputBackground = Color(0xFF383A40);
  static const blurple = Color(0xFF5865F2);
  static const textNormal = Color(0xFFDBDEE1);
  static const textMuted = Color(0xFF949BA4);
  static const online = Color(0xFF23A55A);
  static const idle = Color(0xFFF0B232);
  static const offline = Color(0xFF80848E);
  static const danger = Color(0xFFDA373C);
}

ThemeData buildDiscordTheme() {
  final base = ThemeData.dark(useMaterial3: true);
  return base.copyWith(
    scaffoldBackgroundColor: DiscordColors.chatBackground,
    colorScheme: const ColorScheme.dark(
      primary: DiscordColors.blurple,
      surface: DiscordColors.chatBackground,
      error: DiscordColors.danger,
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: DiscordColors.chatBackground,
      foregroundColor: DiscordColors.textNormal,
      elevation: 1,
    ),
    drawerTheme: const DrawerThemeData(
      backgroundColor: DiscordColors.sidebarBackground,
    ),
    snackBarTheme: const SnackBarThemeData(
      backgroundColor: DiscordColors.railBackground,
      contentTextStyle: TextStyle(color: DiscordColors.textNormal),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: DiscordColors.inputBackground,
      hintStyle: const TextStyle(color: DiscordColors.textMuted),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: BorderSide.none,
      ),
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: DiscordColors.blurple,
        foregroundColor: Colors.white,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
      ),
    ),
  );
}
