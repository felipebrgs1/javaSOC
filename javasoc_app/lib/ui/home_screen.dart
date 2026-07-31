import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../state/auth_provider.dart';
import '../state/chat_provider.dart';
import '../theme.dart';
import 'widgets/message_input.dart';
import 'widgets/message_tile.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _inputKey = GlobalKey<MessageInputState>();
  bool _showMembers = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final auth = context.read<AuthProvider>();
      final user = auth.user;
      if (user != null) {
        context.read<ChatProvider>().connect(user.token, user.username);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final wide = MediaQuery.of(context).size.width >= 900;
    final dmMode = context.watch<ChatProvider>().dmMode;
    if (wide) {
      return Scaffold(
        body: Row(
          children: [
            const _ServerRail(),
            SizedBox(
                width: 240,
                child: dmMode ? const _DmSidebar() : const _ChannelSidebar()),
            Expanded(child: _ChatArea(
              inputKey: _inputKey,
              onToggleMembers: dmMode
                  ? null
                  : () => setState(() => _showMembers = !_showMembers),
            )),
            if (_showMembers && !dmMode)
              const SizedBox(width: 240, child: _MembersPanel()),
          ],
        ),
      );
    }
    return Scaffold(
      appBar: AppBar(
        title: const _ChatTitle(),
        actions: [
          if (!dmMode)
            Builder(
              builder: (context) => IconButton(
                icon: const Icon(Icons.people_outline),
                onPressed: () => Scaffold.of(context).openEndDrawer(),
              ),
            ),
        ],
      ),
      drawer: Drawer(
        child: Row(children: [
          const _ServerRail(),
          Expanded(child: dmMode ? const _DmSidebar() : const _ChannelSidebar()),
        ]),
      ),
      endDrawer: const Drawer(child: _MembersPanel()),
      body: _ChatArea(inputKey: _inputKey),
    );
  }
}

class _ServerRail extends StatelessWidget {
  const _ServerRail();

  @override
  Widget build(BuildContext context) {
    final chat = context.watch<ChatProvider>();
    return Container(
      width: 72,
      color: DiscordColors.railBackground,
      child: Column(
        children: [
          const SizedBox(height: 12),
          _RailButton(
            tooltip: 'Mensagens diretas',
            selected: chat.dmMode,
            badge: chat.unreadDmCount,
            child: const Icon(Icons.chat_bubble, color: Colors.white),
            onTap: () {
              chat.openDmHome();
              if (Scaffold.of(context).isDrawerOpen) Navigator.pop(context);
            },
          ),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 20, vertical: 4),
            child: Divider(color: DiscordColors.sidebarBackground),
          ),
          _RailButton(
            tooltip: ChatProvider.serverName,
            selected: !chat.dmMode,
            child: const Text('JS',
                style: TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                    fontSize: 18)),
            onTap: () {
              chat.closeDm();
              if (Scaffold.of(context).isDrawerOpen) Navigator.pop(context);
            },
          ),
        ],
      ),
    );
  }
}

class _RailButton extends StatelessWidget {
  final String tooltip;
  final bool selected;
  final Widget child;
  final VoidCallback onTap;
  final int badge;

  const _RailButton({
    required this.tooltip,
    required this.selected,
    required this.child,
    required this.onTap,
    this.badge = 0,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Tooltip(
        message: tooltip,
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            InkWell(
              onTap: onTap,
              borderRadius: BorderRadius.circular(24),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 150),
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: selected
                      ? DiscordColors.blurple
                      : DiscordColors.sidebarBackground,
                  borderRadius: BorderRadius.circular(selected ? 16 : 24),
                ),
                child: Center(child: child),
              ),
            ),
            if (badge > 0)
              Positioned(
                right: -2,
                bottom: -2,
                child: Container(
                  padding: const EdgeInsets.all(4),
                  decoration: const BoxDecoration(
                      color: DiscordColors.danger, shape: BoxShape.circle),
                  child: Text('$badge',
                      style:
                          const TextStyle(fontSize: 10, color: Colors.white)),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _ChannelSidebar extends StatelessWidget {
  const _ChannelSidebar();

  @override
  Widget build(BuildContext context) {
    return Container(
      color: DiscordColors.sidebarBackground,
      child: Column(
        children: [
          Container(
            height: 56,
            alignment: Alignment.centerLeft,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            decoration: const BoxDecoration(
              border:
                  Border(bottom: BorderSide(color: DiscordColors.railBackground)),
            ),
            child: const Text(
              ChatProvider.serverName,
              style: TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 16,
                  color: DiscordColors.textNormal),
            ),
          ),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.symmetric(vertical: 8),
              children: [
                const _SectionLabel('CANAIS DE TEXTO'),
                for (final channel in ChatProvider.textChannels)
                  _ChannelTile(channel: channel),
                const SizedBox(height: 12),
                const _SectionLabel('CANAIS DE VOZ'),
                for (final channel in ChatProvider.voiceChannels)
                  _ChannelTile(channel: channel),
              ],
            ),
          ),
          const _UserFooter(),
        ],
      ),
    );
  }
}

class _UserFooter extends StatelessWidget {
  const _UserFooter();

  @override
  Widget build(BuildContext context) {
    final auth = context.read<AuthProvider>();
    return Container(
      color: DiscordColors.railBackground,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      child: Row(
        children: [
          CircleAvatar(
            radius: 16,
            backgroundColor: DiscordColors.blurple,
            child: Text(
              (auth.user?.username ?? '?')[0].toUpperCase(),
              style: const TextStyle(color: Colors.white, fontSize: 14),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              auth.user?.username ?? '',
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: DiscordColors.textNormal),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.logout,
                size: 20, color: DiscordColors.textMuted),
            tooltip: 'Sair',
            onPressed: () {
              context.read<ChatProvider>().disconnect();
              auth.logout();
            },
          ),
        ],
      ),
    );
  }
}

class _DmSidebar extends StatelessWidget {
  const _DmSidebar();

  @override
  Widget build(BuildContext context) {
    final chat = context.watch<ChatProvider>();
    final peers = chat.dmPeers;
    return Container(
      color: DiscordColors.sidebarBackground,
      child: Column(
        children: [
          Container(
            height: 56,
            alignment: Alignment.centerLeft,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            decoration: const BoxDecoration(
              border:
                  Border(bottom: BorderSide(color: DiscordColors.railBackground)),
            ),
            child: const Text(
              'Mensagens Diretas',
              style: TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 16,
                  color: DiscordColors.textNormal),
            ),
          ),
          Expanded(
            child: peers.isEmpty
                ? const Center(
                    child: Padding(
                      padding: EdgeInsets.all(16),
                      child: Text(
                        'Nenhuma conversa ainda.\nEscolha um contato na lista ao lado.',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                            color: DiscordColors.textMuted, fontSize: 13),
                      ),
                    ),
                  )
                : ListView(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    children: [
                      const _SectionLabel('CONVERSAS'),
                      for (final peer in peers) _DmTile(peer: peer),
                    ],
                  ),
          ),
          const _UserFooter(),
        ],
      ),
    );
  }
}

class _DmTile extends StatelessWidget {
  final String peer;

  const _DmTile({required this.peer});

  @override
  Widget build(BuildContext context) {
    final chat = context.watch<ChatProvider>();
    final selected = chat.activeDmPeer == peer;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 1),
      child: ListTile(
        dense: true,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
        selected: selected,
        selectedTileColor: Colors.white.withValues(alpha: 0.08),
        leading: CircleAvatar(
          radius: 14,
          backgroundColor: DiscordColors.blurple,
          child: Text(
            peer.isNotEmpty ? peer[0].toUpperCase() : '?',
            style: const TextStyle(color: Colors.white, fontSize: 12),
          ),
        ),
        title: Text(
          peer,
          style: TextStyle(
            color: selected ? Colors.white : DiscordColors.textMuted,
            fontWeight: selected ? FontWeight.bold : FontWeight.normal,
          ),
        ),
        onTap: () {
          chat.openDm(peer);
          if (Scaffold.of(context).isDrawerOpen) Navigator.pop(context);
        },
      ),
    );
  }
}

class _SectionLabel extends StatelessWidget {
  final String text;

  const _SectionLabel(this.text);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
      child: Text(
        text,
        style: const TextStyle(
            fontSize: 11,
            fontWeight: FontWeight.bold,
            color: DiscordColors.textMuted,
            letterSpacing: 0.5),
      ),
    );
  }
}

class _ChannelTile extends StatelessWidget {
  final ChannelInfo channel;

  const _ChannelTile({required this.channel});

  @override
  Widget build(BuildContext context) {
    final chat = context.watch<ChatProvider>();
    final selected = chat.currentChannel ==
        '${ChatProvider.serverName}:${channel.name}';
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 1),
      child: ListTile(
        dense: true,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
        selected: selected,
        selectedTileColor: Colors.white.withValues(alpha: 0.08),
        leading: Icon(
          channel.isVoice ? Icons.volume_up : Icons.tag,
          size: 20,
          color: DiscordColors.textMuted,
        ),
        title: Text(
          channel.name,
          style: TextStyle(
            color: selected ? Colors.white : DiscordColors.textMuted,
            fontWeight: selected ? FontWeight.bold : FontWeight.normal,
          ),
        ),
        onTap: () {
          if (channel.isVoice) {
            ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
                content: Text('Canais de voz ainda não suportados no app')));
            return;
          }
          chat.closeDm();
          chat.selectChannel(channel.name);
          if (Scaffold.of(context).isDrawerOpen) Navigator.pop(context);
        },
      ),
    );
  }
}

class _ChatTitle extends StatelessWidget {
  const _ChatTitle();

  @override
  Widget build(BuildContext context) {
    final chat = context.watch<ChatProvider>();
    final peer = chat.activeDmPeer;
    final channel = chat.currentChannel?.split(':').last ?? '';
    final title = chat.dmMode ? (peer ?? 'Mensagens diretas') : channel;
    return Row(
      children: [
        Icon(chat.dmMode ? Icons.alternate_email : Icons.tag,
            size: 20, color: DiscordColors.textMuted),
        const SizedBox(width: 6),
        Text(title),
        const SizedBox(width: 12),
        _ConnectionDot(state: chat.connectionState),
      ],
    );
  }
}

class _ConnectionDot extends StatelessWidget {
  final ChatConnectionState state;

  const _ConnectionDot({required this.state});

  @override
  Widget build(BuildContext context) {
    final (color, label) = switch (state) {
      ChatConnectionState.connected => (DiscordColors.online, 'conectado'),
      ChatConnectionState.connecting => (DiscordColors.idle, 'conectando...'),
      ChatConnectionState.disconnected => (DiscordColors.danger, 'desconectado'),
    };
    return Row(
      children: [
        Container(
          width: 8,
          height: 8,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 4),
        Text(label,
            style: const TextStyle(fontSize: 12, color: DiscordColors.textMuted)),
      ],
    );
  }
}

class _ChatArea extends StatelessWidget {
  final GlobalKey<MessageInputState> inputKey;
  final VoidCallback? onToggleMembers;

  const _ChatArea({required this.inputKey, this.onToggleMembers});

  @override
  Widget build(BuildContext context) {
    final chat = context.watch<ChatProvider>();
    final wide = MediaQuery.of(context).size.width >= 900;
    return Column(
      children: [
        if (wide)
          Container(
            height: 48,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            decoration: const BoxDecoration(
              border:
                  Border(bottom: BorderSide(color: DiscordColors.railBackground)),
            ),
            child: Row(
              children: [
                const _ChatTitle(),
                const Spacer(),
                if (chat.dmMode)
                  IconButton(
                    icon: const Icon(Icons.close,
                        size: 20, color: DiscordColors.textMuted),
                    tooltip: 'Voltar ao servidor',
                    onPressed: chat.closeDm,
                  ),
                if (onToggleMembers != null)
                  IconButton(
                    icon: const Icon(Icons.people_outline,
                        size: 20, color: DiscordColors.textMuted),
                    tooltip: 'Membros',
                    onPressed: onToggleMembers,
                  ),
              ],
            ),
          ),
        Expanded(
          child: !chat.dmMode
              ? _ChannelMessages(inputKey: inputKey)
              : chat.activeDmPeer != null
                  ? _DmConversation(peer: chat.activeDmPeer!)
                  : const _ContactsList(),
        ),
        if (chat.dmMode && chat.activeDmPeer != null)
          _DmInput(peer: chat.activeDmPeer!)
        else if (!chat.dmMode)
          MessageInput(
            key: inputKey,
            hint:
                'Conversar em #${chat.currentChannel?.split(':').last ?? ''}',
          ),
      ],
    );
  }
}

class _ContactsList extends StatelessWidget {
  const _ContactsList();

  @override
  Widget build(BuildContext context) {
    final chat = context.watch<ChatProvider>();
    final contacts = chat.contacts;
    if (contacts.isEmpty) {
      return const Center(
        child: Text(
          'Nenhum contato por enquanto.\nQuando alguém entrar, vai aparecer aqui.',
          textAlign: TextAlign.center,
          style: TextStyle(color: DiscordColors.textMuted),
        ),
      );
    }
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        const _SectionLabel('CONTATOS'),
        for (final contact in contacts)
          _MemberTile(entry: contact),
      ],
    );
  }
}

class _ChannelMessages extends StatelessWidget {
  final GlobalKey<MessageInputState> inputKey;

  const _ChannelMessages({required this.inputKey});

  @override
  Widget build(BuildContext context) {
    final chat = context.watch<ChatProvider>();
    final channel = chat.currentChannel?.split(':').last;
    if (channel == null) {
      return const Center(child: Text('Selecione um canal'));
    }
    final messages = chat.messagesFor(channel);
    if (messages.isEmpty) {
      return Center(
        child: Text(
          'Nenhuma mensagem em #$channel ainda.\nSeja o primeiro a conversar!',
          textAlign: TextAlign.center,
          style: const TextStyle(color: DiscordColors.textMuted),
        ),
      );
    }
    return ListView.builder(
      reverse: true,
      padding: const EdgeInsets.symmetric(vertical: 8),
      itemCount: messages.length,
      itemBuilder: (context, index) {
        final message = messages[messages.length - 1 - index];
        return MessageTile(
          key: ValueKey(message.id),
          message: message,
          isMine: message.from == chat.username,
          onReact: (emoji) => chat.toggleReaction(message, emoji),
          onEdit: () => inputKey.currentState?.startEditing(message),
          onDelete: () => _confirmDelete(context, chat, message),
        );
      },
    );
  }

  void _confirmDelete(
      BuildContext context, ChatProvider chat, ChatMessageModel message) {
    showDialog(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: DiscordColors.sidebarBackground,
        title: const Text('Excluir mensagem'),
        content: const Text('Tem certeza? Essa ação não pode ser desfeita.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
                backgroundColor: DiscordColors.danger),
            onPressed: () {
              chat.deleteMessage(message.id);
              Navigator.pop(dialogContext);
            },
            child: const Text('Excluir'),
          ),
        ],
      ),
    );
  }
}

class _MembersPanel extends StatelessWidget {
  const _MembersPanel();

  @override
  Widget build(BuildContext context) {
    final chat = context.watch<ChatProvider>();
    final presence = chat.contacts;
    final online = presence
        .where((p) => p.status != PresenceStatus.offline)
        .toList()
      ..sort((a, b) => a.username.compareTo(b.username));
    final offline = presence
        .where((p) => p.status == PresenceStatus.offline)
        .toList()
      ..sort((a, b) => a.username.compareTo(b.username));
    return Container(
      color: DiscordColors.sidebarBackground,
      child: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          _SectionLabel('ONLINE — ${online.length}'),
          for (final member in online) _MemberTile(entry: member),
          if (offline.isNotEmpty) ...[
            const SizedBox(height: 8),
            _SectionLabel('OFFLINE — ${offline.length}'),
            for (final member in offline) _MemberTile(entry: member),
          ],
        ],
      ),
    );
  }
}

class _MemberTile extends StatelessWidget {
  final PresenceEntry entry;

  const _MemberTile({required this.entry});

  Color get _statusColor => switch (entry.status) {
        PresenceStatus.online => DiscordColors.online,
        PresenceStatus.idle => DiscordColors.idle,
        PresenceStatus.offline => DiscordColors.offline,
      };

  @override
  Widget build(BuildContext context) {
    final chat = context.read<ChatProvider>();
    final isMe = entry.username == chat.username;
    final dimmed = entry.status == PresenceStatus.offline;
    return ListTile(
      dense: true,
      leading: Stack(
        children: [
          CircleAvatar(
            radius: 14,
            backgroundColor: DiscordColors.blurple,
            child: Text(
              entry.username.isNotEmpty ? entry.username[0].toUpperCase() : '?',
              style: const TextStyle(color: Colors.white, fontSize: 12),
            ),
          ),
          Positioned(
            right: 0,
            bottom: 0,
            child: Container(
              width: 10,
              height: 10,
              decoration: BoxDecoration(
                color: _statusColor,
                shape: BoxShape.circle,
                border: Border.all(color: DiscordColors.sidebarBackground, width: 2),
              ),
            ),
          ),
        ],
      ),
      title: Text(
        entry.username + (isMe ? ' (você)' : ''),
        style: TextStyle(
          color: dimmed ? DiscordColors.textMuted : DiscordColors.textNormal,
        ),
      ),
      onTap: isMe
          ? null
          : () {
              chat.openDm(entry.username);
              if (Scaffold.of(context).isEndDrawerOpen) Navigator.pop(context);
            },
    );
  }
}

class _DmConversation extends StatelessWidget {
  final String peer;

  const _DmConversation({required this.peer});

  @override
  Widget build(BuildContext context) {
    final chat = context.watch<ChatProvider>();
    final messages = chat.dmsWith(peer);
    if (messages.isEmpty) {
      return Center(
        child: Text(
          'Início da conversa com @$peer\n(DMs são temporárias e só chegam se o usuário estiver online)',
          textAlign: TextAlign.center,
          style: const TextStyle(color: DiscordColors.textMuted),
        ),
      );
    }
    return ListView.builder(
      reverse: true,
      padding: const EdgeInsets.all(16),
      itemCount: messages.length,
      itemBuilder: (context, index) {
        final message = messages[messages.length - 1 - index];
        final mine = message.from == chat.username;
        return Align(
          alignment: mine ? Alignment.centerRight : Alignment.centerLeft,
          child: Container(
            margin: const EdgeInsets.symmetric(vertical: 3),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            constraints: const BoxConstraints(maxWidth: 420),
            decoration: BoxDecoration(
              color: mine
                  ? DiscordColors.blurple
                  : DiscordColors.sidebarBackground,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(message.from,
                    style: const TextStyle(
                        fontSize: 11, color: DiscordColors.textMuted)),
                Text(message.content,
                    style: const TextStyle(color: DiscordColors.textNormal)),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _DmInput extends StatefulWidget {
  final String peer;

  const _DmInput({required this.peer});

  @override
  State<_DmInput> createState() => _DmInputState();
}

class _DmInputState extends State<_DmInput> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _send() {
    final text = _controller.text.trim();
    if (text.isEmpty) return;
    context.read<ChatProvider>().sendDm(widget.peer, text);
    _controller.clear();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
      child: Container(
        decoration: BoxDecoration(
          color: DiscordColors.inputBackground,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          children: [
            Expanded(
              child: TextField(
                controller: _controller,
                decoration: InputDecoration(
                  hintText: 'Conversar com @${widget.peer}',
                  border: InputBorder.none,
                  filled: false,
                ),
                onSubmitted: (_) => _send(),
              ),
            ),
            IconButton(
              icon: const Icon(Icons.send, color: DiscordColors.blurple),
              onPressed: _send,
            ),
          ],
        ),
      ),
    );
  }
}
