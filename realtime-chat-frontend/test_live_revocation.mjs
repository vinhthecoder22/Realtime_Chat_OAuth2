import { Client } from '@stomp/stompjs';

const BASE_URL = 'http://localhost:8080/api/v1';
const WS_URL = 'ws://localhost:8080/ws-chat/websocket';

async function request(path, options = {}) {
  const url = `${BASE_URL}${path}`;
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  const res = await fetch(url, { ...options, headers });
  const text = await res.text();
  let json;
  try { json = JSON.parse(text); } catch { json = text; }
  return { status: res.status, ok: res.ok, body: json };
}

function createStompClient(token) {
  return new Client({
    brokerURL: WS_URL,
    connectHeaders: { Authorization: `Bearer ${token}` },
    debug: () => {},
    reconnectDelay: 0
  });
}

async function connectStomp(client) {
  return new Promise((resolve, reject) => {
    client.onConnect = (frame) => resolve(frame);
    client.onStompError = (frame) => reject(new Error(frame.headers['message'] || 'STOMP_ERROR'));
    client.onWebSocketError = (err) => reject(err);
    client.activate();
  });
}

async function runRevocationVerification() {
  console.log('======================================================================');
  console.log('LIVE VERIFICATION: Group WebSocket Authorization & Outbound Revocation');
  console.log('======================================================================');
  const ts = Date.now();

  const userA = { username: `rev_admin_${ts}`, password: 'password123', email: `rev_admin_${ts}@test.com` };
  const userB = { username: `rev_member_${ts}`, password: 'password123', email: `rev_member_${ts}@test.com` };
  const userD = { username: `rev_active_${ts}`, password: 'password123', email: `rev_active_${ts}@test.com` };
  const userC = { username: `rev_outsider_${ts}`, password: 'password123', email: `rev_outsider_${ts}@test.com` };

  // 1. Register & Login Users A, B, D, C
  for (const u of [userA, userB, userD, userC]) {
    const reg = await request('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ ...u, repeatPassword: u.password })
    });
    if (!reg.ok) throw new Error(`Registration failed for ${u.username}: ${JSON.stringify(reg.body)}`);
    const logRes = await request('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username: u.username, password: u.password })
    });
    if (!logRes.ok) throw new Error(`Login failed for ${u.username}: ${JSON.stringify(logRes.body)}`);
    u.token = logRes.body.data.token;
    const payload = JSON.parse(Buffer.from(u.token.split('.')[1], 'base64').toString());
    u.id = Number(payload.sub);
  }
  console.log(`[PASS] Registered & Logged in: Admin A (ID ${userA.id}), Member B (ID ${userB.id}), Active Member D (ID ${userD.id}), Outsider C (ID ${userC.id})`);

  // Friendships: A <-> B, A <-> D (required to add friends to groups)
  const addFriend = async (u1, u2) => {
    const reqRes = await request('/friend-requests', {
      method: 'POST',
      headers: { Authorization: `Bearer ${u1.token}` },
      body: JSON.stringify({ receiverId: u2.id })
    });
    const reqId = reqRes.body.data.id;
    await request(`/friend-requests/${reqId}/accept`, {
      method: 'PATCH',
      headers: { Authorization: `Bearer ${u2.token}` }
    });
  };
  await addFriend(userA, userB);
  await addFriend(userA, userD);
  console.log('[PASS] Friendships established: A <-> B and A <-> D');

  // 2. Admin A creates Group Chat with B and D
  const createGroupRes = await request('/conversations/group', {
    method: 'POST',
    headers: { Authorization: `Bearer ${userA.token}` },
    body: JSON.stringify({
      name: `Revocation Test Group ${ts}`,
      memberUserIds: [userB.id, userD.id]
    })
  });
  if (!createGroupRes.ok) throw new Error(`Group creation failed: ${JSON.stringify(createGroupRes.body)}`);
  const groupId = createGroupRes.body.data.id;
  console.log(`[PASS] Group created with ID ${groupId} by Admin A, with members B and D`);

  // 3. Connect independent STOMP sessions
  const clientA = createStompClient(userA.token);
  const clientB = createStompClient(userB.token);
  const clientD = createStompClient(userD.token);
  const clientC = createStompClient(userC.token);

  await connectStomp(clientA);
  await connectStomp(clientB);
  await connectStomp(clientD);
  await connectStomp(clientC);
  console.log('[PASS] 4 Independent STOMP sessions connected (A, B, D, C)');

  // 4. Verify Outsider C cannot subscribe to group topic
  let cSubError = null;
  clientC.onStompError = (f) => { cSubError = f.headers['message']; };
  clientC.subscribe(`/topic/conversation.${groupId}`, () => {});
  await new Promise(r => setTimeout(r, 600));
  if (cSubError && cSubError.includes('UNAUTHORIZED')) {
    console.log(`[PASS] Verification 1: Outsider C rejected on SUBSCRIBE with UNAUTHORIZED (${cSubError})`);
  } else {
    throw new Error(`Outsider C was NOT rejected properly: ${cSubError}`);
  }

  // 5. Members B and D subscribe to /topic/conversation.{groupId}
  const bReceivedMessages = [];
  const dReceivedMessages = [];

  const subB = clientB.subscribe(`/topic/conversation.${groupId}`, (frame) => {
    bReceivedMessages.push(JSON.parse(frame.body));
  });
  const subD = clientD.subscribe(`/topic/conversation.${groupId}`, (frame) => {
    dReceivedMessages.push(JSON.parse(frame.body));
  });
  await new Promise(r => setTimeout(r, 600));

  // 6. Send message 1 from Admin A while B and D are ACTIVE
  clientA.publish({
    destination: '/app/chat.sendMessage',
    body: JSON.stringify({
      conversationId: groupId,
      content: 'MSG-1: Active state broadcast',
      type: 'CHAT'
    })
  });
  await new Promise(r => setTimeout(r, 800));

  const bGotMsg1 = bReceivedMessages.some(m => m.content === 'MSG-1: Active state broadcast');
  const dGotMsg1 = dReceivedMessages.some(m => m.content === 'MSG-1: Active state broadcast');
  if (bGotMsg1 && dGotMsg1) {
    console.log('[PASS] Verification 2: Initial message delivered to both active members B and D');
  } else {
    throw new Error(`Initial message delivery failed: B=${bGotMsg1}, D=${dGotMsg1}`);
  }

  // 7. Admin A KICKS Member B via REST API
  // Note: Client B intentionally KEEPS subscription subB open! (Simulating uncooperative/malicious client)
  console.log('\n--- Executing KICK of Member B by Admin A ---');
  const kickRes = await request(`/conversations/${groupId}/members/${userB.id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${userA.token}` }
  });
  if (!kickRes.ok) throw new Error(`Kick failed: ${JSON.stringify(kickRes.body)}`);
  console.log('[PASS] Admin A successfully kicked Member B via REST DELETE');

  // Small delay for transaction commit + cache invalidation
  await new Promise(r => setTimeout(r, 500));

  // Clear tracked messages
  bReceivedMessages.length = 0;
  dReceivedMessages.length = 0;

  // 8. Admin A sends Message 2 AFTER B is kicked
  clientA.publish({
    destination: '/app/chat.sendMessage',
    body: JSON.stringify({
      conversationId: groupId,
      content: 'MSG-2: Secret message after B was kicked',
      type: 'CHAT'
    })
  });
  // Active member D sends Message 3 AFTER B is kicked
  clientD.publish({
    destination: '/app/chat.sendMessage',
    body: JSON.stringify({
      conversationId: groupId,
      content: 'MSG-3: Another message from active member D',
      type: 'CHAT'
    })
  });
  await new Promise(r => setTimeout(r, 1200));

  // 9. VERIFICATION: Outbound Frame Suppression on Kicked Member B
  const dGotMsg2 = dReceivedMessages.some(m => m.content === 'MSG-2: Secret message after B was kicked');
  const dGotMsg3 = dReceivedMessages.some(m => m.content === 'MSG-3: Another message from active member D');
  const bGotMsg2 = bReceivedMessages.some(m => m.content === 'MSG-2: Secret message after B was kicked');
  const bGotMsg3 = bReceivedMessages.some(m => m.content === 'MSG-3: Another message from active member D');

  if (dGotMsg2 && dGotMsg3) {
    console.log('[PASS] Verification 3: Active Member D received both MSG-2 and MSG-3 without interruption');
  } else {
    throw new Error(`Active member D missed post-kick messages! D received: ${JSON.stringify(dReceivedMessages)}`);
  }

  if (!bGotMsg2 && !bGotMsg3 && bReceivedMessages.length === 0) {
    console.log('[PASS] Verification 4: Kicked Member B RECEIVED ZERO post-kick frames on open subscription! (Outbound revocation VERIFIED)');
  } else {
    throw new Error(`SECURITY BREACH: Kicked member B received frames after revocation! Messages: ${JSON.stringify(bReceivedMessages)}`);
  }

  // 10. VERIFICATION: Kicked Member B cannot send messages
  let bSendError = null;
  clientB.subscribe('/user/queue/errors', (f) => {
    bSendError = JSON.parse(f.body);
  });
  clientB.publish({
    destination: '/app/chat.sendMessage',
    body: JSON.stringify({
      conversationId: groupId,
      content: 'Attempting to send after being kicked',
      type: 'CHAT'
    })
  });
  await new Promise(r => setTimeout(r, 800));
  if (bSendError && (bSendError.error?.includes('not an active member') || bSendError.error?.includes('Access denied'))) {
    console.log(`[PASS] Verification 5: Kicked Member B blocked from sending messages (${bSendError.error})`);
  } else {
    throw new Error(`Kicked member B send was not rejected: ${JSON.stringify(bSendError)}`);
  }

  // 11. VERIFICATION: Kicked Member B cannot read history via REST
  const bHistoryRes = await request(`/chat/${groupId}/messages`, {
    headers: { Authorization: `Bearer ${userB.token}` }
  });
  if (bHistoryRes.status === 403) {
    console.log('[PASS] Verification 6: Kicked Member B blocked from reading conversation history (403 Forbidden)');
  } else {
    throw new Error(`Kicked member history access returned HTTP ${bHistoryRes.status} instead of 403`);
  }

  // 12. VERIFICATION: Kicked Member B reconnects and cannot re-subscribe
  clientB.deactivate();
  const clientB_reconnect = createStompClient(userB.token);
  await connectStomp(clientB_reconnect);
  let bResubError = null;
  clientB_reconnect.onStompError = (f) => { bResubError = f.headers['message']; };
  clientB_reconnect.subscribe(`/topic/conversation.${groupId}`, () => {});
  await new Promise(r => setTimeout(r, 600));

  if (bResubError && bResubError.includes('UNAUTHORIZED')) {
    console.log(`[PASS] Verification 7: Reconnected Member B blocked on re-subscribe (${bResubError})`);
  } else {
    throw new Error(`Reconnected member B re-subscribe was not rejected: ${bResubError}`);
  }

  clientA.deactivate();
  clientD.deactivate();
  clientC.deactivate();
  clientB_reconnect.deactivate();

  console.log('\n======================================================================');
  console.log('ALL 7 LIVE REVOCATION & AUTHORIZATION VERIFICATIONS: PASSED 100%');
  console.log('======================================================================');
}

runRevocationVerification().catch(err => {
  console.error('[FATAL ERROR]:', err);
  process.exit(1);
});
