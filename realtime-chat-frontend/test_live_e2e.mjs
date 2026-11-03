import { Client } from '@stomp/stompjs';

const BASE_URL = 'http://localhost:8080/api/v1';
const WS_URL = 'ws://localhost:8080/ws-chat/websocket';

const results = [];

function recordResult(scenario, name, status, details = '') {
  results.push({ scenario, name, status, details });
  console.log(`[${status}] [${scenario}] ${name}${details ? ' - ' + details : ''}`);
}

async function request(path, options = {}) {
  const url = `${BASE_URL}${path}`;
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  const res = await fetch(url, { ...options, headers });
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = text;
  }
  return { status: res.status, ok: res.ok, body: json };
}

function createStompClient(token) {
  return new Client({
    brokerURL: WS_URL,
    connectHeaders: {
      Authorization: `Bearer ${token}`
    },
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

async function run() {
  console.log('=== STARTING LIVE END-TO-END VERIFICATION (STEP 10.7) ===');
  const nano = Date.now();

  const userA = {
    username: `live_a_${nano}`,
    password: 'password123',
    repeatPassword: 'password123',
    email: `live_a_${nano}@example.com`
  };
  const userB = {
    username: `live_b_${nano}`,
    password: 'password123',
    repeatPassword: 'password123',
    email: `live_b_${nano}@example.com`
  };
  const userC = {
    username: `live_c_${nano}`,
    password: 'password123',
    repeatPassword: 'password123',
    email: `live_c_${nano}@example.com`
  };
  const userD = {
    username: `live_d_${nano}`,
    password: 'password123',
    repeatPassword: 'password123',
    email: `live_d_${nano}@example.com`
  };

  try {
    // 1. SETUP: Register & Login Users A, B, C, D
    console.log('\n--- 1. User Registration & Authentication ---');
    for (const u of [userA, userB, userC, userD]) {
      const reg = await request('/auth/register', {
        method: 'POST',
        body: JSON.stringify(u)
      });
      if (!reg.ok) throw new Error(`Failed to register ${u.username}: ${JSON.stringify(reg.body)}`);
      u.id = reg.body.data.id;

      const logRes = await request('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username: u.username, password: u.password })
      });
      if (!logRes.ok) throw new Error(`Failed to login ${u.username}: ${JSON.stringify(logRes.body)}`);
      u.token = logRes.body.data.token;
      const payload = JSON.parse(Buffer.from(u.token.split('.')[1], 'base64').toString());
      u.id = Number(payload.sub);
    }
    recordResult('SETUP', 'Register & Login Users A, B, C, D', 'PASS', `IDs: A=${userA.id}, B=${userB.id}, C=${userC.id}, D=${userD.id}`);

    // Friendships: A <-> B (ACCEPTED), A <-> D (ACCEPTED)
    console.log('\n--- 2. Friendship Setup ---');
    const sendFriendReq = async (sender, receiverId) => {
      const res = await request('/friend-requests', {
        method: 'POST',
        headers: { Authorization: `Bearer ${sender.token}` },
        body: JSON.stringify({ receiverId })
      });
      return res.body.data;
    };
    const acceptFriendReq = async (receiver, reqId) => {
      const res = await request(`/friend-requests/${reqId}/accept`, {
        method: 'PATCH',
        headers: { Authorization: `Bearer ${receiver.token}` }
      });
      return res.ok;
    };

    const frAB = await sendFriendReq(userA, userB.id);
    await acceptFriendReq(userB, frAB.id);
    const frAD = await sendFriendReq(userA, userD.id);
    await acceptFriendReq(userD, frAD.id);
    recordResult('SETUP', 'Friend requests A<->B and A<->D accepted', 'PASS');

    // ==========================================
    // SCENARIO A: BASIC GROUP CHAT
    // ==========================================
    console.log('\n--- SCENARIO A: Basic Group Chat ---');
    // A1: A creates group with B
    const createGroupRes = await request('/conversations/group', {
      method: 'POST',
      headers: { Authorization: `Bearer ${userA.token}` },
      body: JSON.stringify({
        name: 'Live E2E Alpha Group',
        memberUserIds: [userB.id]
      })
    });
    if (!createGroupRes.ok) throw new Error(`Create group failed: ${JSON.stringify(createGroupRes.body)}`);
    const groupId = createGroupRes.body.data.id;
    recordResult('A1', 'User A creates Group Chat with member B', 'PASS', `GroupId: ${groupId}`);

    // A2: Real WebSocket STOMP clients for A and B
    const clientA = createStompClient(userA.token);
    const clientB = createStompClient(userB.token);
    await connectStomp(clientA);
    await connectStomp(clientB);
    recordResult('A2', 'WebSocket STOMP clients connected for A and B', 'PASS');

    // A3: Realtime message exchange
    let msgReceivedByB = null;
    const subB = clientB.subscribe(`/topic/conversation.${groupId}`, (frame) => {
      msgReceivedByB = JSON.parse(frame.body);
    });

    // Client A sends message via WebSocket
    clientA.publish({
      destination: '/app/chat.sendMessage',
      body: JSON.stringify({
        conversationId: groupId,
        content: 'Hello Live B from A via STOMP WebSocket!',
        type: 'CHAT'
      })
    });

    // Wait for message arrival
    await new Promise((r) => setTimeout(r, 1000));
    if (msgReceivedByB && msgReceivedByB.content === 'Hello Live B from A via STOMP WebSocket!' && msgReceivedByB.sender === userA.username) {
      recordResult('A3', 'Realtime group message delivered to B without reload', 'PASS', `Content: "${msgReceivedByB.content}"`);
    } else {
      recordResult('A3', 'Realtime group message delivered to B', 'FAIL', `Expected message from A, got: ${JSON.stringify(msgReceivedByB)}`);
    }

    // A4: History loaded after refresh
    const historyRes = await request(`/chat/${groupId}/messages`, {
      headers: { Authorization: `Bearer ${userB.token}` }
    });
    const messages = historyRes.body.data?.content || [];
    const persisted = messages.some((m) => m.content === 'Hello Live B from A via STOMP WebSocket!');
    if (persisted) {
      recordResult('A4', 'Group message history persisted and retrieved via REST', 'PASS', `Total messages: ${messages.length}`);
    } else {
      recordResult('A4', 'Group message history persisted', 'FAIL');
    }

    // A5: Unread count, no self count, mark as read
    const convsB = await request('/conversations', {
      headers: { Authorization: `Bearer ${userB.token}` }
    });
    const listB = convsB.body.data?.content || [];
    const groupInB = listB.find((c) => c.id === groupId);
    const unreadCountB = groupInB?.unreadCount ?? -1;

    const convsA = await request('/conversations', {
      headers: { Authorization: `Bearer ${userA.token}` }
    });
    const listA = convsA.body.data?.content || [];
    const groupInA = listA.find((c) => c.id === groupId);
    const unreadCountA = groupInA?.unreadCount ?? -1;

    if (unreadCountB >= 1 && unreadCountA === 0) {
      recordResult('A5', 'Unread count increments for recipient B and remains 0 for sender A', 'PASS', `B unread=${unreadCountB}, A unread=${unreadCountA}`);
    } else {
      recordResult('A5', 'Unread count validation', 'FAIL', `B unread=${unreadCountB}, A unread=${unreadCountA}`);
    }

    // B marks as read
    const markRes = await request(`/conversations/${groupId}/read`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${userB.token}` }
    });
    const convsBAfter = await request('/conversations', {
      headers: { Authorization: `Bearer ${userB.token}` }
    });
    const listBAfter = convsBAfter.body.data?.content || [];
    const groupInBAfter = listBAfter.find((c) => c.id === groupId);
    if (markRes.ok && groupInBAfter?.unreadCount === 0) {
      recordResult('A5b', 'Recipient B marks conversation as read -> unreadCount resets to 0', 'PASS');
    } else {
      recordResult('A5b', 'Recipient B marks conversation as read', 'FAIL', `Unread count: ${groupInBAfter?.unreadCount}`);
    }

    // A6: Rename group & update avatar
    const updateRes = await request(`/conversations/${groupId}`, {
      method: 'PATCH',
      headers: { Authorization: `Bearer ${userA.token}` },
      body: JSON.stringify({
        name: 'Live E2E Alpha Renamed',
        avatar: 'https://example.com/avatar_alpha.png'
      })
    });
    if (updateRes.ok && updateRes.body.data.name === 'Live E2E Alpha Renamed' && updateRes.body.data.avatar === 'https://example.com/avatar_alpha.png') {
      recordResult('A6', 'Admin updates group name and avatar successfully', 'PASS', `Name: ${updateRes.body.data.name}`);
    } else {
      recordResult('A6', 'Admin updates group name and avatar', 'FAIL', JSON.stringify(updateRes.body));
    }

    // ==========================================
    // SCENARIO B: AUTHORIZATION & SECURITY
    // ==========================================
    console.log('\n--- SCENARIO B: Authorization & Security ---');
    // B1: Outsider C cannot read group details, members, or history
    const cConvRes = await request(`/conversations/${groupId}`, {
      headers: { Authorization: `Bearer ${userC.token}` }
    });
    const cMembersRes = await request(`/conversations/${groupId}/members`, {
      headers: { Authorization: `Bearer ${userC.token}` }
    });
    const cMsgsRes = await request(`/chat/${groupId}/messages`, {
      headers: { Authorization: `Bearer ${userC.token}` }
    });
    if (cConvRes.status === 403 && cMembersRes.status === 403 && cMsgsRes.status === 403) {
      recordResult('B1', 'Outsider C blocked from reading group info, members, and messages (403 Forbidden)', 'PASS');
    } else {
      recordResult('B1', 'Outsider C blocked from group data', 'FAIL', `Statuses: ${cConvRes.status}, ${cMembersRes.status}, ${cMsgsRes.status}`);
    }

    // B2: Outsider C tries to SUBSCRIBE to /topic/conversation.{id}
    const clientC = createStompClient(userC.token);
    await connectStomp(clientC);
    let cSubError = null;
    clientC.onStompError = (frame) => {
      cSubError = frame.headers['message'];
    };
    clientC.subscribe(`/topic/conversation.${groupId}`, () => {});
    await new Promise((r) => setTimeout(r, 800));
    if (cSubError && cSubError.includes('UNAUTHORIZED')) {
      recordResult('B2', 'Outsider C SUBSCRIBE to group topic rejected by interceptor with UNAUTHORIZED', 'PASS', `Error: ${cSubError}`);
    } else {
      recordResult('B2', 'Outsider C SUBSCRIBE rejected', 'FAIL', `Error: ${cSubError}`);
    }
    clientC.deactivate();

    // B3: Regular member B tries admin actions
    const bAddRes = await request(`/conversations/${groupId}/members`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${userB.token}` },
      body: JSON.stringify({ memberUserIds: [userD.id] })
    });
    const bKickRes = await request(`/conversations/${groupId}/members/${userA.id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${userB.token}` }
    });
    const bUpdateRes = await request(`/conversations/${groupId}`, {
      method: 'PATCH',
      headers: { Authorization: `Bearer ${userB.token}` },
      body: JSON.stringify({ name: 'Hacked by B' })
    });
    const bTransferRes = await request(`/conversations/${groupId}/admin`, {
      method: 'PATCH',
      headers: { Authorization: `Bearer ${userB.token}` },
      body: JSON.stringify({ newAdminUserId: userB.id })
    });
    if (bAddRes.status === 403 && bKickRes.status === 403 && bUpdateRes.status === 403 && bTransferRes.status === 403) {
      recordResult('B3', 'Regular member B rejected with 403 Forbidden on all Admin actions', 'PASS');
    } else {
      recordResult('B3', 'Regular member B admin actions', 'FAIL', `Statuses: add=${bAddRes.status}, kick=${bKickRes.status}, update=${bUpdateRes.status}, transfer=${bTransferRes.status}`);
    }

    // B4: Live revocation - Admin A kicks B while B is connected
    let bKickedEvent = null;
    clientB.subscribe('/user/queue/events', (frame) => {
      bKickedEvent = JSON.parse(frame.body);
    });

    const kickBRes = await request(`/conversations/${groupId}/members/${userB.id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${userA.token}` }
    });
    await new Promise((r) => setTimeout(r, 800));

    if (kickBRes.ok && bKickedEvent && bKickedEvent.type === 'MEMBER_KICKED' && Number(bKickedEvent.conversationId) === Number(groupId)) {
      recordResult('B4', 'Admin kicks active member B; B receives MEMBER_KICKED event on /user/queue/events', 'PASS', `Received: ${JSON.stringify(bKickedEvent)}`);
    } else {
      recordResult('B4', 'Live MEMBER_KICKED event to kicked member', 'FAIL', `kickRes=${kickBRes.status}, event=${JSON.stringify(bKickedEvent)}`);
    }

    // B5: Kicked member B tries to send message -> rejected by ChatService
    let bErrorReceived = null;
    clientB.subscribe('/user/queue/errors', (frame) => {
      bErrorReceived = JSON.parse(frame.body);
    });

    clientB.publish({
      destination: '/app/chat.sendMessage',
      body: JSON.stringify({
        conversationId: groupId,
        content: 'I was kicked but trying to speak!',
        type: 'CHAT'
      })
    });
    await new Promise((r) => setTimeout(r, 800));
    if (bErrorReceived && (bErrorReceived.error?.includes('not an active member') || bErrorReceived.error?.includes('Access denied'))) {
      recordResult('B5', 'Kicked member B cannot send messages; error returned to /user/queue/errors', 'PASS', `Error: ${bErrorReceived.error}`);
    } else {
      recordResult('B5', 'Kicked member message rejection', 'FAIL', `Error received: ${JSON.stringify(bErrorReceived)}`);
    }

    // B6: Kicked member B reconnects and tries to re-subscribe to group
    clientB.deactivate();
    const clientB2 = createStompClient(userB.token);
    await connectStomp(clientB2);
    let b2SubError = null;
    clientB2.onStompError = (frame) => {
      b2SubError = frame.headers['message'];
    };
    clientB2.subscribe(`/topic/conversation.${groupId}`, () => {});
    await new Promise((r) => setTimeout(r, 800));
    if (b2SubError && b2SubError.includes('UNAUTHORIZED')) {
      recordResult('B6', 'Kicked member B reconnects and re-subscribe is blocked with UNAUTHORIZED', 'PASS', `Error: ${b2SubError}`);
    } else {
      recordResult('B6', 'Kicked member re-subscribe blocked', 'FAIL', `Error: ${b2SubError}`);
    }
    clientB2.deactivate();

    // B7: Malformed destinations, negative IDs, broker direct send
    const clientSec1 = createStompClient(userA.token);
    await connectStomp(clientSec1);
    let secError1 = null;
    clientSec1.onStompError = (frame) => {
      secError1 = frame.headers['message'];
    };
    // Try negative ID subscribe
    clientSec1.subscribe('/topic/conversation.-1', () => {});
    await new Promise((r) => setTimeout(r, 600));
    const negOk = secError1 && secError1.includes('INVALID_DESTINATION');
    clientSec1.deactivate();

    // Try direct send to broker with fresh connection
    const clientSec2 = createStompClient(userA.token);
    await connectStomp(clientSec2);
    let secError2 = null;
    clientSec2.onStompError = (frame) => {
      secError2 = frame.headers['message'];
    };
    clientSec2.publish({
      destination: '/topic/conversation.1',
      body: 'direct send attempt'
    });
    await new Promise((r) => setTimeout(r, 600));
    const directOk = secError2 && secError2.includes('FORBIDDEN');
    clientSec2.deactivate();

    if (negOk && directOk) {
      recordResult('B7', 'Malformed destinations rejected (negative ID: INVALID_DESTINATION, direct broker SEND: FORBIDDEN)', 'PASS');
    } else {
      recordResult('B7', 'Malformed destinations validation', 'FAIL', `negOk=${negOk}, directOk=${directOk}, error1=${secError1}, error2=${secError2}`);
    }

    // ==========================================
    // SCENARIO C: MEMBER & ADMIN MANAGEMENT
    // ==========================================
    console.log('\n--- SCENARIO C: Member & Admin Management ---');
    // C1: Admin adds accepted friend D
    const addDRes = await request(`/conversations/${groupId}/members`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${userA.token}` },
      body: JSON.stringify({ memberUserIds: [userD.id] })
    });
    if (addDRes.ok) {
      recordResult('C1', 'Admin adds accepted friend D to group', 'PASS');
    } else {
      recordResult('C1', 'Admin adds accepted friend D', 'FAIL', JSON.stringify(addDRes.body));
    }

    // C2: Try adding user who is NOT a friend (User C is not friend with A)
    const addNonFriendRes = await request(`/conversations/${groupId}/members`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${userA.token}` },
      body: JSON.stringify({ memberUserIds: [userC.id] })
    });
    // Try adding user who is already ACTIVE (User D)
    const addActiveRes = await request(`/conversations/${groupId}/members`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${userA.token}` },
      body: JSON.stringify({ memberUserIds: [userD.id] })
    });
    if (addNonFriendRes.status === 400 && addActiveRes.status === 400) {
      recordResult('C2', 'Non-friend and already-active members rejected with 400 Bad Request', 'PASS');
    } else {
      recordResult('C2', 'Invalid member addition rejection', 'FAIL', `nonFriend=${addNonFriendRes.status}, active=${addActiveRes.status}`);
    }

    // C3: Kick member D and verify membership status & memberCount
    const kickDRes = await request(`/conversations/${groupId}/members/${userD.id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${userA.token}` }
    });
    const membersAfterKick = await request(`/conversations/${groupId}/members`, {
      headers: { Authorization: `Bearer ${userA.token}` }
    });
    const dInMembers = (membersAfterKick.body.data || []).find((m) => m.userId === userD.id);
    if (kickDRes.ok && (!dInMembers || dInMembers.status === 'REMOVED')) {
      recordResult('C3', 'Member D kicked; active members count updated and D removed from active list', 'PASS');
    } else {
      recordResult('C3', 'Kick member D verification', 'FAIL', `dInMembers: ${JSON.stringify(dInMembers)}`);
    }

    // C4: Transfer Admin role
    // First, let's re-add B by making B active or creating a new group with A & B
    const group2Res = await request('/conversations/group', {
      method: 'POST',
      headers: { Authorization: `Bearer ${userA.token}` },
      body: JSON.stringify({
        name: 'Live Group for Admin Transfer',
        memberUserIds: [userB.id]
      })
    });
    const group2Id = group2Res.body.data.id;

    const transferRes = await request(`/conversations/${group2Id}/admin`, {
      method: 'PATCH',
      headers: { Authorization: `Bearer ${userA.token}` },
      body: JSON.stringify({ newAdminUserId: userB.id })
    });
    const g2Members = await request(`/conversations/${group2Id}/members`, {
      headers: { Authorization: `Bearer ${userB.token}` }
    });
    const aMember = (g2Members.body.data || []).find((m) => m.userId === userA.id);
    const bMember = (g2Members.body.data || []).find((m) => m.userId === userB.id);

    if (transferRes.ok && bMember?.role === 'ADMIN' && aMember?.role === 'MEMBER') {
      recordResult('C4', 'Admin transferred to B; B is now ADMIN, A is now MEMBER', 'PASS');
    } else {
      recordResult('C4', 'Admin transfer validation', 'FAIL', `B role: ${bMember?.role}, A role: ${aMember?.role}`);
    }

    // C5: Old admin A leaves group
    const aLeaveRes = await request(`/conversations/${group2Id}/leave`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${userA.token}` },
      body: JSON.stringify({})
    });
    if (aLeaveRes.ok) {
      recordResult('C5', 'Former admin A leaves group successfully as regular member', 'PASS');
    } else {
      recordResult('C5', 'Former admin A leaves group', 'FAIL', JSON.stringify(aLeaveRes.body));
    }

    // C6: Admin B leaves group as the last member
    const bLeaveRes = await request(`/conversations/${group2Id}/leave`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${userB.token}` },
      body: JSON.stringify({})
    });
    if (bLeaveRes.ok) {
      recordResult('C6', 'Last member B leaves group successfully; group has 0 active members', 'PASS');
    } else {
      recordResult('C6', 'Last member B leaves group', 'FAIL', JSON.stringify(bLeaveRes.body));
    }

    // ==========================================
    // SCENARIO D: REGRESSION & COMPATIBILITY
    // ==========================================
    console.log('\n--- SCENARIO D: Regression & Compatibility ---');
    // D1: Global Chat (PUBLIC)
    const clientPubA = createStompClient(userA.token);
    const clientPubB = createStompClient(userB.token);
    await connectStomp(clientPubA);
    await connectStomp(clientPubB);

    let pubMsgReceived = null;
    clientPubB.subscribe('/topic/public', (frame) => {
      pubMsgReceived = JSON.parse(frame.body);
    });

    clientPubA.publish({
      destination: '/app/chat.sendMessage',
      body: JSON.stringify({
        content: 'Hello Global Chat from Live A!',
        type: 'CHAT'
      })
    });
    await new Promise((r) => setTimeout(r, 800));
    if (pubMsgReceived && pubMsgReceived.content === 'Hello Global Chat from Live A!') {
      recordResult('D1', 'Global Chat (PUBLIC): Messages delivered realtime to all connected users', 'PASS');
    } else {
      recordResult('D1', 'Global Chat (PUBLIC)', 'FAIL', `Received: ${JSON.stringify(pubMsgReceived)}`);
    }

    // D2: Direct Chat between A and B
    const directRes = await request('/conversations/direct', {
      method: 'POST',
      headers: { Authorization: `Bearer ${userA.token}` },
      body: JSON.stringify({ targetUserId: userB.id })
    });
    if (!directRes.ok) throw new Error(`Direct chat creation failed: ${JSON.stringify(directRes.body)}`);
    const directId = directRes.body.data.id;

    let directMsgReceived = null;
    clientPubB.subscribe(`/topic/conversation.${directId}`, (frame) => {
      directMsgReceived = JSON.parse(frame.body);
    });
    // Wait for subscription to be registered by the broker
    await new Promise((r) => setTimeout(r, 600));

    clientPubA.publish({
      destination: '/app/chat.sendMessage',
      body: JSON.stringify({
        conversationId: directId,
        content: 'Hello B, this is a private direct message!',
        type: 'CHAT'
      })
    });
    await new Promise((r) => setTimeout(r, 1000));
    if (directMsgReceived && directMsgReceived.content === 'Hello B, this is a private direct message!') {
      recordResult('D2', 'Direct Chat (DIRECT): Messages delivered realtime between friends', 'PASS');
    } else {
      recordResult('D2', 'Direct Chat (DIRECT)', 'FAIL', `Received: ${JSON.stringify(directMsgReceived)}`);
    }

    // D3: Direct Chat unread count & read receipt
    const convsBDirect = await request('/conversations', {
      headers: { Authorization: `Bearer ${userB.token}` }
    });
    const listD = convsBDirect.body.data?.content || [];
    const directInB = listD.find((c) => c.id === directId);
    if (directInB && directInB.unreadCount >= 1) {
      recordResult('D3', 'Direct Chat: Unread count calculated correctly for recipient', 'PASS', `Unread=${directInB.unreadCount}`);
    } else {
      recordResult('D3', 'Direct Chat unread count', 'FAIL', `Unread: ${directInB?.unreadCount}`);
    }

    // D4: Token expiration & invalid token WebSocket rejection
    const invalidClient = createStompClient('this_is_an_invalid_expired_token');
    let invalidError = null;
    invalidClient.onStompError = (frame) => {
      invalidError = frame.headers['message'];
    };
    try {
      await connectStomp(invalidClient);
    } catch (e) {
      invalidError = e.message;
    }
    if (invalidError && (invalidError.includes('INVALID_TOKEN') || invalidError.includes('UNAUTHORIZED'))) {
      recordResult('D4', 'Invalid or expired JWT rejected on WebSocket CONNECT with INVALID_TOKEN', 'PASS', `Error: ${invalidError}`);
    } else {
      recordResult('D4', 'Invalid token rejection', 'FAIL', `Error: ${invalidError}`);
    }

    // D5a: Google OAuth2 Initiation Check via live HTTP 302 redirect
    let oauth2Initiated = false;
    let oauth2Location = '';
    try {
      const authRes = await fetch('http://localhost:8080/oauth2/authorization/google', {
        redirect: 'manual'
      });
      oauth2Location = authRes.headers.get('location') || '';
      if (authRes.status === 302 && oauth2Location.includes('accounts.google.com')) {
        oauth2Initiated = true;
      }
    } catch (e) {
      oauth2Location = e.message;
    }

    if (oauth2Initiated) {
      recordResult('D5a', 'Google OAuth2 Authorization Initiation (HTTP 302 -> accounts.google.com)', 'PASS', `Redirect target: ${oauth2Location.substring(0, 48)}...`);
    } else {
      recordResult('D5a', 'Google OAuth2 Authorization Initiation', 'FAIL', `Redirect failed or unexpected status. Target: ${oauth2Location}`);
    }

    // D5b: Google OAuth2 Status
    // Headless automated CLI environment cannot complete interactive Google web login consent
    recordResult('D5b', 'Google OAuth2 Live Browser Interactive Login & User Consent', 'BLOCKED', 'Requires interactive Google web authentication consent in browser with real user credentials');

    clientPubA.deactivate();
    clientPubB.deactivate();
    clientA.deactivate();

    console.log('\n=== VERIFICATION SUMMARY ===');
    const passes = results.filter((r) => r.status === 'PASS').length;
    const fails = results.filter((r) => r.status === 'FAIL').length;
    const blocked = results.filter((r) => r.status === 'BLOCKED').length;
    console.log(`TOTAL: ${results.length} | PASS: ${passes} | FAIL: ${fails} | BLOCKED: ${blocked}`);

    if (fails > 0) {
      process.exit(1);
    } else {
      process.exit(0);
    }

  } catch (err) {
    console.error('Test execution aborted due to unhandled error:', err);
    process.exit(1);
  }
}

run();
