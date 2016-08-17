package org.zunpeng.shiro.session.redis;

import org.apache.shiro.session.Session;
import org.apache.shiro.session.mgt.ValidatingSession;
import org.apache.shiro.session.mgt.eis.CachingSessionDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

/**
 * Created by dapeng on 16/8/14.
 */
public class RedisSessionDAO extends CachingSessionDAO {

	private static Logger logger = LoggerFactory.getLogger(RedisSessionDAO.class);

	private String keyPrefix = "shiro_redis_session:";

	private String deleteChannel = "shiro_redis_session:delete";

	private int timeToLiveSeconds = 1800; // Expiration of Jedis's key, unit: second

	private RedisTemplate<String, Session> redisTemplate;

	public RedisSessionDAO(RedisTemplate redisTemplate){
		this.redisTemplate = redisTemplate;
	}

	/**
	 * 更新 session 到 Redis.
	 * @param session
	 */
	@Override
	protected void doUpdate(Session session) {
		// 如果会话过期/停止，没必要再更新了
		if (session instanceof ValidatingSession && !((ValidatingSession) session).isValid()) {
			logger.debug("=> Invalid session.");
			return;
		}

		logger.debug("=> Update session with ID [{}]", session.getId());

		BoundValueOperations<String, Session> boundValueOperations = redisTemplate.boundValueOps(getKey(session.getId()));
		boundValueOperations.set(session);
		boundValueOperations.expire(timeToLiveSeconds, TimeUnit.SECONDS);
	}

	/**
	 * 从 Redis 删除 session，并且发布消息通知其它 Server 上的 Cache 删除 session.
	 * @param session
	 */
	@Override
	protected void doDelete(Session session) {
		logger.debug("=> Delete session with ID [{}]", session.getId());

		redisTemplate.delete(getKey(session.getId()));
		// 发布消息通知其它 Server 上的 cache 删除 session.
		// redisManager.publish(deleteChannel, SerializationUtils.sessionIdToString(session));

		// 放在其它类里用一个 daemon 线程执行，删除 cache 中的 session
		// jedis.subscribe(new JedisPubSub() {
		//     @Override
		//     public void onMessage(String channel, String message) {
		//         // 1. deserialize message to sessionId
		//         // 2. Session session = getCachedSession(sessionId);
		//         // 3. uncache(session);
		//     }
		// }, deleteChannel);
	}

	/**
	 * DefaultSessionManager 创建完 session 后会调用该方法。
	 * 把 session 保持到 Redis。
	 * 返回 Session ID；主要此处返回的 ID.equals(session.getId())
	 */
	@Override
	protected Serializable doCreate(Session session) {
		logger.debug("=> Create session with ID [{}]", session.getId());

		// 创建一个Id并设置给Session
		Serializable sessionId = this.generateSessionId(session);
		assignSessionId(session, sessionId);

		// session 由 Redis 缓存失效决定
		BoundValueOperations<String, Session> boundValueOperations = redisTemplate.boundValueOps(getKey(session.getId()));
		boundValueOperations.set(session);
		boundValueOperations.expire(timeToLiveSeconds, TimeUnit.SECONDS);

		return sessionId;
	}

	/**
	 * 从 Redis 上读取 session，并缓存到本地 Cache.
	 * @param sessionId
	 * @return
	 */
	@Override
	protected Session doReadSession(Serializable sessionId) {
		logger.debug("=> Read session with ID [{}]", sessionId);

		if(!redisTemplate.hasKey(getKey(sessionId))){
			return null;
		}

		BoundValueOperations<String, Session> boundValueOperations = redisTemplate.boundValueOps(getKey(sessionId));

		// 例如 Redis 调用 flushdb 情况了所有的数据，读到的 session 就是空的
		Session session = boundValueOperations.get();
		super.cache(session, session.getId());

		return session;
	}

	public String getKey(Serializable sessionId){
		return keyPrefix + sessionId.toString();
	}
}
