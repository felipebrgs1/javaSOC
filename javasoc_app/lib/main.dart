import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'config.dart';
import 'services/api_service.dart';
import 'services/chat_service.dart';
import 'state/auth_provider.dart';
import 'state/chat_provider.dart';
import 'theme.dart';
import 'ui/home_screen.dart';
import 'ui/login_screen.dart';

void main() {
  runApp(const JavaSocApp());
}

class JavaSocApp extends StatefulWidget {
  const JavaSocApp({super.key});

  @override
  State<JavaSocApp> createState() => _JavaSocAppState();
}

class _JavaSocAppState extends State<JavaSocApp> {
  String? _baseUrl;

  @override
  void initState() {
    super.initState();
    AppConfig.loadBaseUrl().then((url) => setState(() => _baseUrl = url));
  }

  @override
  Widget build(BuildContext context) {
    final baseUrl = _baseUrl;
    if (baseUrl == null) {
      return const MaterialApp(
        home: Scaffold(body: Center(child: CircularProgressIndicator())),
      );
    }
    final api = ApiService(baseUrl);
    return MultiProvider(
      providers: [
        Provider<ApiService>.value(value: api),
        ChangeNotifierProvider(create: (_) => AuthProvider()..restore(api)),
        ChangeNotifierProvider(
          create: (_) => ChatProvider(ChatService(), api, baseUrl),
        ),
      ],
      child: MaterialApp(
        title: 'JavaSOC',
        debugShowCheckedModeBanner: false,
        theme: buildDiscordTheme(),
        home: const _Root(),
      ),
    );
  }
}

class _Root extends StatelessWidget {
  const _Root();

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    if (auth.restoring) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (auth.user == null) return const LoginScreen();
    return const HomeScreen();
  }
}
