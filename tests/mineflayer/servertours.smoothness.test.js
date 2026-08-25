const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const mineflayer = require('mineflayer')
const minecraftData = require('minecraft-data')

const ROUTE_NAME = process.env.ST_ROUTE_NAME || 'complex_path'
const SAMPLE_MS = Number(process.env.ST_SAMPLE_MS || 100)
const VERSION = process.env.MC_VERSION || '1.21.5'
const TEXT_DISPLAY_TYPE_ID = minecraftData(VERSION)?.entitiesByName?.text_display?.id

assert.ok(TEXT_DISPLAY_TYPE_ID != null, `${VERSION} does not expose the Java text_display entity`)

const POINTS = [
  { x: 0, y: 96, z: -24, yaw: -59, pitch: -14, seconds: 3.4, type: 'INTERPOLATE', label: 'launch_sweep' },
  { x: 10, y: 99, z: -18, yaw: -50, pitch: 7, seconds: 4.4, type: 'INTERPOLATE', label: 'ridge_climb' },
  { x: 22, y: 97, z: -8, yaw: -16, pitch: -12, seconds: 4.2, type: 'INTERPOLATE', label: 'east_cut' },
  { x: 26, y: 100, z: 6, yaw: 30, pitch: 7, seconds: 4.7, type: 'INTERPOLATE', label: 'upper_bank' },
  { x: 18, y: 98, z: 20, yaw: 67, pitch: 8, seconds: 4.4, type: 'INTERPOLATE', label: 'south_arc' },
  { x: 4, y: 96, z: 26, yaw: 113, pitch: -11, seconds: 4.5, type: 'INTERPOLATE', label: 'wide_cross' },
  { x: -10, y: 99, z: 20, yaw: 131, pitch: 6, seconds: 5.3, type: 'INTERPOLATE', label: 'west_lift' },
  { x: -24, y: 97, z: 8, yaw: 172, pitch: -12, seconds: 4.1, type: 'INTERPOLATE', label: 'left_loop' },
  { x: -26, y: 100, z: -6, yaw: -145, pitch: 7, seconds: 4.9, type: 'INTERPOLATE', label: 'back_bank' },
  { x: -16, y: 98, z: -20, yaw: -113, pitch: 8, seconds: 4.4, type: 'INTERPOLATE', label: 'northwest_run' },
  { x: -2, y: 96, z: -26, yaw: -60, pitch: -7, seconds: 4.7, type: 'INTERPOLATE', label: 'center_return' },
  { x: 12, y: 98, z: -18, yaw: -32, pitch: 3, seconds: 5.4, type: 'INTERPOLATE', label: 'second_pass' },
  { x: 22, y: 97, z: -2, yaw: 23, pitch: 8, seconds: 4.4, type: 'INTERPOLATE', label: 'inner_curve' },
  { x: 16, y: 95, z: 12, yaw: 67, pitch: 4, seconds: 4.4, type: 'INTERPOLATE', label: 'descending_turn' },
  { x: 2, y: 94, z: 18, yaw: 135, pitch: -4, seconds: 4.0, type: 'INTERPOLATE', label: 'low_sweep' },
  { x: -8, y: 95, z: 8, yaw: -146, pitch: -4, seconds: 4.1, type: 'INTERPOLATE', label: 'final_hook' },
  { x: 0, y: 96, z: -4, yaw: 0, pitch: 0, seconds: 1.5, type: 'STATIONARY', label: 'finish_hover' }
]

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function createBot() {
  const bot = mineflayer.createBot({
    host: process.env.MC_HOST || '127.0.0.1',
    port: Number(process.env.MC_PORT || 25566),
    username: process.env.MC_USERNAME || 'TestBot',
    version: VERSION,
    auth: process.env.MC_AUTH || 'offline'
  })

  bot.observedMessages = []
  bot.knownTextDisplays = new Set()
  bot.displayTargetPositions = new Map()
  bot.activeProtocolCameraId = null
  bot.displayLifecycle = {
    spawns: 0,
    cameraTargets: 0,
    moves: 0,
    destroys: 0,
    restores: 0
  }
  bot.on('messagestr', (message) => {
    bot.observedMessages.push(message)
  })
  bot.on('error', (error) => {
    bot.lastError = error
  })
  bot.on('kicked', (reason) => {
    bot.lastKickReason = reason
  })
  bot._client.on('spawn_entity', (packet) => {
    if (packet.type !== TEXT_DISPLAY_TYPE_ID) return
    bot.knownTextDisplays.add(packet.entityId)
    bot.displayTargetPositions.set(packet.entityId, {
      x: packet.x,
      y: packet.y,
      z: packet.z,
      source: 'display_spawn'
    })
    bot.displayLifecycle.spawns += 1
  })
  bot._client.on('camera', (packet) => {
    bot.activeProtocolCameraId = packet.cameraId
    if (bot.knownTextDisplays.has(packet.cameraId)) {
      bot.displayLifecycle.cameraTargets += 1
    } else if (packet.cameraId === bot.entity?.id) {
      bot.displayLifecycle.restores += 1
    }
  })
  bot._client.on('entity_teleport', (packet) => {
    if (!bot.knownTextDisplays.has(packet.entityId)) return
    // minecraft-protocol 1.21.5 still describes the pre-1.21.5 tail of this
    // packet. XYZ is decoded before that stale tail and is reliable; yaw,
    // pitch and interpolation as rendered by the client are deliberately not
    // asserted by this headless regression.
    if (![packet.x, packet.y, packet.z].every(Number.isFinite)) return
    bot.displayTargetPositions.set(packet.entityId, {
      x: packet.x,
      y: packet.y,
      z: packet.z,
      source: 'display_teleport'
    })
    bot.displayLifecycle.moves += 1
  })
  bot._client.on('entity_destroy', (packet) => {
    for (const entityId of packet.entityIds) {
      if (bot.knownTextDisplays.has(entityId)) {
        bot.displayLifecycle.destroys += 1
      }
    }
  })

  return bot
}

async function failFast(bot) {
  if (bot.lastError) throw bot.lastError
  if (bot.lastKickReason) throw new Error(`Bot kicked: ${bot.lastKickReason}`)
}

async function waitForReady(bot, maxMs = 30000) {
  const started = Date.now()
  while (Date.now() - started < maxMs) {
    await failFast(bot)
    if (bot.entity) return
    await wait(250)
  }
  throw new Error('Timed out waiting for bot spawn')
}

async function waitForMessageSince(bot, matcher, startIndex, maxMs = 20000) {
  const started = Date.now()
  while (Date.now() - started < maxMs) {
    await failFast(bot)
    const found = bot.observedMessages.slice(startIndex).find((message) => matcher.test(message))
    if (found) return found
    await wait(250)
  }
  throw new Error(`Timed out waiting for message ${matcher}. Saw: ${bot.observedMessages.join(' | ')}`)
}

async function sendCommand(bot, command, delayMs = 450) {
  bot.chat(command)
  await wait(delayMs)
  await failFast(bot)
}

function sampleClientPosition(bot) {
  const entityId = bot.activeProtocolCameraId
  const position = bot.displayTargetPositions.get(entityId)
  if (!bot.knownTextDisplays.has(entityId) || !position) {
    throw new Error('Could not sample a current Java TextDisplay camera target')
  }
  return {
    t: Date.now(),
    x: position.x,
    y: position.y,
    z: position.z,
    source: position.source,
    entityId
  }
}

function distance(a, b) {
  const dx = a.x - b.x
  const dy = a.y - b.y
  const dz = a.z - b.z
  return Math.sqrt(dx * dx + dy * dy + dz * dz)
}

function percentile(values, p) {
  if (values.length === 0) return 0
  const sorted = [...values].sort((a, b) => a - b)
  const index = Math.min(sorted.length - 1, Math.max(0, Math.ceil((p / 100) * sorted.length) - 1))
  return sorted[index]
}

function mean(values) {
  if (values.length === 0) return 0
  return values.reduce((sum, value) => sum + value, 0) / values.length
}

function stddev(values) {
  if (values.length < 2) return 0
  const avg = mean(values)
  return Math.sqrt(mean(values.map((value) => (value - avg) ** 2)))
}

function range(samples, axis) {
  if (samples.length === 0) return 0
  const values = samples.map((sample) => sample[axis])
  return Math.max(...values) - Math.min(...values)
}

function pathDistance(points) {
  let total = 0
  for (let i = 1; i < points.length; i += 1) {
    total += distance(points[i - 1], points[i])
  }
  return total
}

function analyzeSamples(samples) {
  const steps = []
  for (let i = 1; i < samples.length; i += 1) {
    const previous = samples[i - 1]
    const current = samples[i]
    const dt = (current.t - previous.t) / 1000
    if (dt <= 0 || dt > 0.75) continue
    const stepDistance = distance(current, previous)
    steps.push({
      index: i,
      t: current.t,
      dt,
      distance: stepDistance,
      speed: stepDistance / dt
    })
  }

  const teleportLikeSteps = steps.filter((step) => step.distance > 15 || step.speed > 35)
  const smoothSteps = steps.filter((step) => step.distance <= 15 && step.speed <= 35)
  const accelerations = []
  for (let i = 1; i < smoothSteps.length; i += 1) {
    const previous = smoothSteps[i - 1]
    const current = smoothSteps[i]
    const dt = Math.max(current.dt, 0.001)
    accelerations.push(Math.abs(current.speed - previous.speed) / dt)
  }

  const jerks = []
  for (let i = 1; i < accelerations.length; i += 1) {
    const dt = Math.max(smoothSteps[i + 1]?.dt || SAMPLE_MS / 1000, 0.001)
    jerks.push(Math.abs(accelerations[i] - accelerations[i - 1]) / dt)
  }

  const speeds = smoothSteps.map((step) => step.speed)
  const tickIntervals = steps.map((step) => step.dt * 1000)
  const sourceCounts = samples.reduce((counts, sample) => {
    counts[sample.source] = (counts[sample.source] || 0) + 1
    return counts
  }, {})
  const midRouteTeleports = teleportLikeSteps.filter((step) => {
    const relative = step.index / Math.max(samples.length - 1, 1)
    return relative > 0.08 && relative < 0.92
  })

  const observedPathDistance = smoothSteps.reduce((sum, step) => sum + step.distance, 0)
  const speedStd = stddev(speeds)
  const p95Acceleration = percentile(accelerations, 95)
  const p95Jerk = percentile(jerks, 95)
  const score = Math.max(0, Math.min(100,
    100
      - midRouteTeleports.length * 25
      - Math.min(30, speedStd * 3)
      - Math.min(25, p95Acceleration * 0.35)
      - Math.min(20, p95Jerk * 0.015)
  ))

  return {
    sampleCount: samples.length,
    stepCount: steps.length,
    smoothStepCount: smoothSteps.length,
    teleportLikeStepCount: teleportLikeSteps.length,
    midRouteTeleportLikeStepCount: midRouteTeleports.length,
    observedPathDistance: Number(observedPathDistance.toFixed(3)),
    plannedPathDistance: Number(pathDistance(POINTS).toFixed(3)),
    displayTargetBoundingBox: {
      x: Number(range(samples, 'x').toFixed(3)),
      y: Number(range(samples, 'y').toFixed(3)),
      z: Number(range(samples, 'z').toFixed(3))
    },
    sampleSources: sourceCounts,
    tickMs: {
      mean: Number(mean(tickIntervals).toFixed(2)),
      p95: Number(percentile(tickIntervals, 95).toFixed(2)),
      max: Number(Math.max(0, ...tickIntervals).toFixed(2))
    },
    speedBlocksPerSecond: {
      mean: Number(mean(speeds).toFixed(3)),
      stddev: Number(speedStd.toFixed(3)),
      p95: Number(percentile(speeds, 95).toFixed(3)),
      max: Number(Math.max(0, ...speeds).toFixed(3))
    },
    accelerationBlocksPerSecond2: {
      mean: Number(mean(accelerations).toFixed(3)),
      p95: Number(p95Acceleration.toFixed(3)),
      max: Number(Math.max(0, ...accelerations).toFixed(3))
    },
    jerkBlocksPerSecond3: {
      mean: Number(mean(jerks).toFixed(3)),
      p95: Number(p95Jerk.toFixed(3)),
      max: Number(Math.max(0, ...jerks).toFixed(3))
    },
    smoothnessScoreHeuristic: Number(score.toFixed(1)),
    teleportLikeSteps: teleportLikeSteps.slice(0, 10).map((step) => ({
      index: step.index,
      distance: Number(step.distance.toFixed(3)),
      speed: Number(step.speed.toFixed(3))
    })),
    notes: [
      'teleportLikeStepCount may include the initial target or an intentional hard rebase',
      'midRouteTeleportLikeStepCount is the primary discontinuity signal',
      'samples are server-sent TextDisplay target positions, not rendered client interpolation',
      'Mineflayer 1.21.5 has a stale entity_teleport tail schema, so rotation is a manual Java-client assertion',
      'score is a heuristic for regression comparison, not a physics-certified metric'
    ]
  }
}

async function createComplexRoute(bot) {
  await sendCommand(bot, '/tour exit', 300)
  await sendCommand(bot, `/tour remove ${ROUTE_NAME}`, 500)
  await sendCommand(bot, '/gamemode spectator @s', 300)
  await sendCommand(bot, `/tour create ${ROUTE_NAME}`, 500)
  await sendCommand(bot, `/tour edit ${ROUTE_NAME}`, 1000)

  for (let index = 0; index < POINTS.length; index += 1) {
    const point = POINTS[index]
    await sendCommand(bot, `/tp @s ${point.x} ${point.y} ${point.z} ${point.yaw} ${point.pitch}`, 80)
    await sendCommand(bot, '/tour createpoint', 450)

    if (point.type !== 'STATIONARY') {
      await sendCommand(bot, `/tour pointsetting type ${point.type}`, 350)
    }
    await sendCommand(bot, `/tour pointsetting secondsVisible ${point.seconds}`, 350)
    await sendCommand(bot, `/tour pointsetting label ${point.label}`, 350)
    if (index === 0) {
      await sendCommand(bot, '/tour pointsetting description Complex path movement route started.', 350)
      await sendCommand(bot, '/tour pointsetting title &aComplex Path\\\\n&7Client Movement Test', 350)
    }
  }

  await sendCommand(bot, '/tour exit', 1000)
  await sendCommand(bot, '/gamemode creative @s', 300)
}

async function playAndAnalyze(bot) {
  const samples = []
  try {
    const startIndex = bot.observedMessages.length
    const lifecycleStart = { ...bot.displayLifecycle }
    bot.chat(`/tour play ${ROUTE_NAME}`)
    const matchedMessage = await waitForMessageSince(bot, /Complex path movement route started/i, startIndex, 8000)
    const cameraTargetStarted = Date.now()
    while (!bot.knownTextDisplays.has(bot.activeProtocolCameraId)) {
      await failFast(bot)
      if (Date.now() - cameraTargetStarted > 5000) {
        throw new Error('Timed out waiting for Java TextDisplay camera target; ensure the DISPLAY backend is active')
      }
      await wait(50)
    }
    const expectedRouteMs = POINTS.reduce((sum, point) => sum + point.seconds * 1000, 0)
    const routeStartAt = Date.now()
    while (Date.now() < routeStartAt + expectedRouteMs - 250) {
      samples.push(sampleClientPosition(bot))
      await wait(SAMPLE_MS)
    }
    const cleanupDeadline = Date.now() + 5000
    while (
      (bot.displayLifecycle.restores === lifecycleStart.restores ||
        bot.displayLifecycle.destroys === lifecycleStart.destroys) &&
      Date.now() < cleanupDeadline
    ) {
      await failFast(bot)
      await wait(50)
    }
    if (bot.displayLifecycle.restores === lifecycleStart.restores) {
      throw new Error('TextDisplay playback did not restore the camera to the player')
    }
    if (bot.displayLifecycle.destroys === lifecycleStart.destroys) {
      throw new Error('TextDisplay playback did not destroy its camera entity')
    }
    return {
      matchedMessage,
      rawSampleCount: samples.length,
      analyzedSampleCount: samples.length,
      report: analyzeSamples(samples),
      displayLifecycle: Object.fromEntries(Object.entries(bot.displayLifecycle).map(([key, value]) => {
        return [key, value - lifecycleStart[key]]
      })),
      firstSample: samples[0],
      lastSample: samples[samples.length - 1]
    }
  } finally {}
}

async function main() {
  const bot = createBot()

  try {
    await waitForReady(bot)
    await wait(1500)

    await createComplexRoute(bot)
    await sendCommand(bot, '/tp @s 0 92 -38 0 0', 750)
    const result = await playAndAnalyze(bot)

    const output = {
      status: 'measured',
      scenario: 'Create a complex route and analyze Java TextDisplay camera target continuity',
      routeName: ROUTE_NAME,
      pointCount: POINTS.length,
      matchedMessage: result.matchedMessage,
      ...result
    }

    const reportDir = path.join(process.cwd(), 'tests', 'mineflayer', 'reports')
    fs.mkdirSync(reportDir, { recursive: true })
    fs.writeFileSync(
      path.join(reportDir, 'servertours-smoothness-report.json'),
      `${JSON.stringify(output, null, 2)}\n`,
      'utf8'
    )

    assert.equal(result.report.midRouteTeleportLikeStepCount, 0, 'route should not have mid-route teleport-like discontinuities')
    assert.ok(result.report.sampleCount >= 120, 'should collect enough samples for smoothness analysis')
    assert.ok(result.report.observedPathDistance >= 180, 'display targets should cover substantial route movement')
    assert.ok(result.report.displayTargetBoundingBox.x >= 45, 'display target X range should cover the complex route')
    assert.ok(result.report.displayTargetBoundingBox.z >= 45, 'display target Z range should cover the complex route')
    assert.ok(result.report.smoothnessScoreHeuristic >= 60, 'target-continuity score should stay above regression threshold')
    assert.ok(result.displayLifecycle.spawns >= 1, 'playback should spawn at least one TextDisplay camera')
    assert.ok(result.displayLifecycle.cameraTargets >= 1, 'playback should target at least one TextDisplay camera')
    assert.ok(result.displayLifecycle.moves >= 120, 'playback should send continuous TextDisplay target updates')
    assert.ok(result.displayLifecycle.restores >= 1, 'playback should restore the camera to the player')
    assert.ok(result.displayLifecycle.destroys >= 1, 'playback should destroy its TextDisplay camera')

    output.status = 'passed'
    fs.writeFileSync(
      path.join(reportDir, 'servertours-smoothness-report.json'),
      `${JSON.stringify(output, null, 2)}\n`,
      'utf8'
    )

    console.log(JSON.stringify(output, null, 2))
  } finally {
    bot.quit()
  }
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
