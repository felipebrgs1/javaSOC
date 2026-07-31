import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

import '../models/models.dart';

class ApiException implements Exception {
  final int statusCode;
  final String message;

  ApiException(this.statusCode, this.message);

  @override
  String toString() => message;
}

class ApiService {
  final String baseUrl;

  static const _timeout = Duration(seconds: 10);

  ApiService(this.baseUrl);

  Future<AuthUser> register(String username, String email, String password) async {
    final res = await http
        .post(
          Uri.parse('$baseUrl/auth/register'),
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode(
              {'username': username, 'email': email, 'password': password}),
        )
        .timeout(_timeout, onTimeout: _timeoutError);
    if (res.statusCode == 201) {
      return AuthUser.fromJson(jsonDecode(res.body) as Map<String, dynamic>);
    }
    throw ApiException(res.statusCode, _errorDetail(res.body));
  }

  Future<AuthUser> login(String username, String password) async {
    final res = await http
        .post(
          Uri.parse('$baseUrl/auth/login'),
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode({'username': username, 'password': password}),
        )
        .timeout(_timeout, onTimeout: _timeoutError);
    if (res.statusCode == 200) {
      return AuthUser.fromJson(jsonDecode(res.body) as Map<String, dynamic>);
    }
    throw ApiException(res.statusCode, _errorDetail(res.body));
  }

  Future<AuthUser> me(String token) async {
    final res = await http.get(
      Uri.parse('$baseUrl/auth/me'),
      headers: {'Authorization': 'Bearer $token'},
    ).timeout(_timeout, onTimeout: _timeoutError);
    if (res.statusCode == 200) {
      return AuthUser.fromJson(jsonDecode(res.body) as Map<String, dynamic>);
    }
    throw ApiException(res.statusCode, _errorDetail(res.body));
  }

  Future<AttachmentView> uploadAttachment({
    required String token,
    required String filename,
    required Uint8List bytes,
    String? contentType,
  }) async {
    final request = http.MultipartRequest('POST', Uri.parse('$baseUrl/api/attachments'));
    request.headers['Authorization'] = 'Bearer $token';
    request.files.add(http.MultipartFile.fromBytes(
      'file',
      bytes,
      filename: filename,
      contentType: contentType != null ? http.MediaType.parse(contentType) : null,
    ));
    final streamed = await request.send();
    final res = await http.Response.fromStream(streamed);
    if (res.statusCode == 201) {
      return AttachmentView.fromJson(jsonDecode(res.body) as Map<String, dynamic>);
    }
    throw ApiException(res.statusCode, _errorDetail(res.body));
  }

  String _errorDetail(String body) {
    try {
      final json = jsonDecode(body) as Map<String, dynamic>;
      return (json['detail'] ?? json['title'] ?? 'Erro inesperado').toString();
    } catch (_) {
      return 'Erro inesperado';
    }
  }

  http.Response _timeoutError() =>
      throw ApiException(0, 'Servidor não respondeu. Verifique a URL e se o backend está rodando.');
}
