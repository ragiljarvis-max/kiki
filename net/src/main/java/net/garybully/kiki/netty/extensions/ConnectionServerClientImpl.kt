package net.garybully.kiki.netty.extensions

import net.garybully.kiki.netty.INettyConnectionTask

class ConnectionServerClientImpl(
    val connectionTask: INettyConnectionTask,
    val serverManager: IServerManager,
    val clientManager: IClientManager
) : INettyConnectionTask by connectionTask, IServerManager by serverManager, IClientManager by clientManager