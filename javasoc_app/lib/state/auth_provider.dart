import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/models.dart';
import '../services/api_service.dart';

class AuthProvider extends ChangeNotifier {
  static const _keyToken = 'auth_token';

  AuthUser? user;
  bool restoring = true;
  String? error;

  Future<void> restore(ApiService api) async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString(_keyToken);
    if (token == null) {
      restoring = false;
      notifyListeners();
      return;
    }
    try {
      user = await api.me(token);
      await _persist(user!.token);
    } catch (_) {
      await prefs.remove(_keyToken);
    }
    restoring = false;
    notifyListeners();
  }

  Future<bool> login(ApiService api, String username, String password) async {
    error = null;
    try {
      user = await api.login(username, password);
      await _persist(user!.token);
      notifyListeners();
      return true;
    } on ApiException catch (e) {
      error = e.message;
      notifyListeners();
      return false;
    }
  }

  Future<bool> register(
      ApiService api, String username, String email, String password) async {
    error = null;
    try {
      user = await api.register(username, email, password);
      await _persist(user!.token);
      notifyListeners();
      return true;
    } on ApiException catch (e) {
      error = e.message;
      notifyListeners();
      return false;
    }
  }

  Future<void> logout() async {
    user = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_keyToken);
    notifyListeners();
  }

  Future<void> _persist(String token) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyToken, token);
  }
}
