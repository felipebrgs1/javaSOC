import 'package:flutter_test/flutter_test.dart';
import 'package:javasoc_app/models/models.dart';

void main() {
  test('ChatMessageModel parses history json', () {
    final message = ChatMessageModel.fromHistoryJson({
      'id': 42,
      'from': 'alice',
      'content': 'hello',
      'timestamp': '2026-07-31T12:00:00Z',
      'reactions': [
        {'emoji': '👍', 'count': 2, 'includesMe': false}
      ],
      'attachments': [
        {
          'id': 7,
          'filename': 'f.png',
          'contentType': 'image/png',
          'sizeBytes': 100,
          'url': '/api/attachments/7/download'
        }
      ],
    });
    expect(message.id, 42);
    expect(message.reactions['👍']?.count, 2);
    expect(message.attachments.single.isImage, isTrue);
  });
}
