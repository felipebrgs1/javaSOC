import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../config.dart';
import '../services/api_service.dart';
import '../state/auth_provider.dart';
import '../theme.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _username = TextEditingController();
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _serverUrl = TextEditingController();

  bool _registerMode = false;
  bool _busy = false;
  bool _showServer = false;

  @override
  void initState() {
    super.initState();
    AppConfig.loadBaseUrl().then((url) => _serverUrl.text = url);
  }

  @override
  void dispose() {
    _username.dispose();
    _email.dispose();
    _password.dispose();
    _serverUrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _busy = true);
    final auth = context.read<AuthProvider>();
    final url = _serverUrl.text.trim().isEmpty
        ? AppConfig.defaultBaseUrl
        : _serverUrl.text.trim();
    await AppConfig.saveBaseUrl(url);
    final api = ApiService(url);
    final ok = _registerMode
        ? await auth.register(api, _username.text.trim(), _email.text.trim(),
            _password.text)
        : await auth.login(api, _username.text.trim(), _password.text);
    if (!mounted) return;
    setState(() => _busy = false);
    if (!ok && auth.error != null) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(auth.error!)));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: DiscordColors.sidebarBackground,
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: Container(
              padding: const EdgeInsets.all(32),
              decoration: BoxDecoration(
                color: DiscordColors.chatBackground,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Form(
                key: _formKey,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      _registerMode ? 'Criar conta' : 'Bem-vindo de volta!',
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        fontSize: 24,
                        fontWeight: FontWeight.bold,
                        color: DiscordColors.textNormal,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      _registerMode
                          ? 'Junte-se ao JavaSOC'
                          : 'Entre para continuar no JavaSOC',
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: DiscordColors.textMuted),
                    ),
                    const SizedBox(height: 24),
                    _label('USUÁRIO'),
                    TextFormField(
                      controller: _username,
                      validator: (v) => (v == null || v.trim().length < 3)
                          ? 'Mínimo 3 caracteres'
                          : null,
                    ),
                    if (_registerMode) ...[
                      const SizedBox(height: 16),
                      _label('EMAIL'),
                      TextFormField(
                        controller: _email,
                        keyboardType: TextInputType.emailAddress,
                        validator: (v) =>
                            (v == null || !v.contains('@')) ? 'Email inválido' : null,
                      ),
                    ],
                    const SizedBox(height: 16),
                    _label('SENHA'),
                    TextFormField(
                      controller: _password,
                      obscureText: true,
                      onFieldSubmitted: (_) => _submit(),
                      validator: (v) =>
                          (v == null || v.length < 6) ? 'Mínimo 6 caracteres' : null,
                    ),
                    const SizedBox(height: 24),
                    FilledButton(
                      onPressed: _busy ? null : _submit,
                      child: Padding(
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        child: _busy
                            ? const SizedBox(
                                height: 18,
                                width: 18,
                                child: CircularProgressIndicator(
                                    strokeWidth: 2, color: Colors.white),
                              )
                            : Text(_registerMode ? 'Registrar' : 'Entrar'),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextButton(
                      onPressed: () =>
                          setState(() => _registerMode = !_registerMode),
                      child: Text(
                        _registerMode
                            ? 'Já tem conta? Entrar'
                            : 'Precisa de uma conta? Registrar',
                        style: const TextStyle(color: DiscordColors.blurple),
                      ),
                    ),
                    TextButton(
                      onPressed: () => setState(() => _showServer = !_showServer),
                      child: Text(
                        _showServer ? 'Ocultar servidor' : 'Servidor: ${_serverUrl.text}',
                        style: const TextStyle(
                            color: DiscordColors.textMuted, fontSize: 12),
                      ),
                    ),
                    if (_showServer)
                      TextFormField(
                        controller: _serverUrl,
                        decoration:
                            const InputDecoration(hintText: 'http://localhost:8080'),
                      ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _label(String text) => Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Text(
          text,
          style: const TextStyle(
            fontSize: 11,
            fontWeight: FontWeight.bold,
            color: DiscordColors.textMuted,
            letterSpacing: 0.5,
          ),
        ),
      );
}
