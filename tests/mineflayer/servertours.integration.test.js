const assert = require('node:assert/strict')
const mineflayer = require('mineflayer')

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function createBot() {
  const bot = mineflayer.createBot({
    host: process.env.MC_HOST || '127.0.0.1',
    port: Number(process.env.MC_PORT || 25566),
    username: process.env.MC_USERNAME || 'TestBot',
    version: process.env.MC_VERSION || '1.21.5',
    auth: process.env.MC_AUTH || 'offline'
  })

  bot.observedMessages = []
  bot.on('messagestr', (message) => {
    bot.observedMessages.push(message)
  })
  bot.on('error', (error) => {
    bot.lastError = error
  })
  bot.on('kicked', (reason) => {
    bot.lastKickReason = reason
  })

  return bot
}

async function waitForReady(bot, maxMs = 30000) {
  const started = Date.now()
  while (Date.now() - started < maxMs) {
    if (bot.lastError) throw bot.lastError
    if (bot.lastKickReason) throw new Error(`Bot kicked: ${bot.lastKickReason}`)
    if (bot.entity) return
    await wait(250)
  }
  throw new Error('Timed out waiting for bot spawn')
}

async function waitForMessage(bot, matcher, maxMs = 20000) {
  const started = Date.now()
  const startIndex = bot.observedMessages.length
  while (Date.now() - started < maxMs) {
    if (bot.lastError) throw bot.lastError
    if (bot.lastKickReason) throw new Error(`Bot kicked: ${bot.lastKickReason}`)
    const found = bot.observedMessages.slice(startIndex).find((message) => matcher.test(message))
    if (found) return found
    await wait(250)
  }
  throw new Error(`Timed out waiting for message ${matcher}. Saw: ${bot.observedMessages.join(' | ')}`)
}

async function main() {
  const bot = createBot()

  try {
    await waitForReady(bot)
    await wait(1000)

    bot.chat('/tour play nmstest')
    const playbackMessage = await waitForMessage(bot, /Integration route loaded/i)
    await wait(3000)

    assert.ok(bot.entity, 'bot should remain connected after tour playback')

    console.log(JSON.stringify({
      status: 'passed',
      scenario: 'ServerTours /tour play nmstest smoke',
      matchedMessage: playbackMessage,
      messageCount: bot.observedMessages.length
    }, null, 2))
  } finally {
    bot.quit()
  }
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
