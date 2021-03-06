package com.cfets.ts.s.platform.gitlab.core;

import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabProject;
import org.gitlab.api.models.GitlabSession;

import com.cfets.ts.s.platform.gitlab.core.exceptions.GitlabException;
import com.cfets.ts.s.platform.gitlab.core.exceptions.GitlabExceptionHandler;
import com.cfets.ts.s.platform.gitlab.core.exceptions.UnknownProjectException;

/**
 * <b>Copyright 2016 中国外汇交易中心 All Rights Reserved</b>
 * 
 * @description：
 * @author pluto
 * @create on 2017年3月4日
 * 
 * @history
 * 
 */
public class ConnectionManager {

	private static final Logger logger = Logger
			.getLogger(ConnectionManager.class);

	private static Repositories repos = new Repositories();

	public static Repositories getRepos() {
		return repos;
	}

	private static HashMap<String, GitlabConnection> connections = new HashMap<String, GitlabConnection>();

	private static Pattern URLPattern = Pattern
			.compile("((?:http|https)://(?:.*))/((?:[^\\/]*?)/(?:[^\\/]*?))$");

	static public GitlabConnection get(TaskRepository repository)
			throws GitlabException {
		return get(repository, false);
	}

	static public GitlabConnection getSafe(TaskRepository repository) {
		try {
			return get(repository);
		} catch (GitlabException e) {
			return null;
		}
	}

	private static String constructURL(TaskRepository repository) {
		String username = repository.getUsername();
		String password = repository.getPassword();
		return repository.getUrl() + "?username=" + username + "&password="
				+ password.hashCode();
	}

	static GitlabConnection validate(TaskRepository repository)
			throws GitlabException {
		try {
			Matcher matcher = URLPattern.matcher(repository.getUrl());
			if (!matcher.find()) {
				throw new GitlabException("Invalid Project-URL!");
			}
			String projectPath = matcher.group(2);
			String host = matcher.group(1);
			String username = repository.getUsername();
			String password = repository.getPassword();
			GitlabSession session = null;
			if (repository.getProperty(TaskRepository.AUTH_TOKEN_USED).equals(
					"true")) {
				session = GitlabAPI.connect(host, password).getCurrentSession();
			} else {
				session = GitlabAPI.connect(host, username, password);
			}

			GitlabAPI api = GitlabAPI.connect(host, session.getPrivateToken());

			if (projectPath.endsWith(".git")) {
				projectPath = projectPath
						.substring(0, projectPath.length() - 4);
			}
			System.out.println("访问工程：" + projectPath);
			List<GitlabProject> projects = api.getProjects();
			for (GitlabProject p : projects) {
				String path = p.getPathWithNamespace();
				String nameSpace = path.substring(0, path.indexOf("/"));
				if ("TS".equals(nameSpace)) {
					repos.put(p.getName(), p.getHttpUrl());
					System.out.println("[" + p.getName() + "] ["
							+ p.getHttpUrl() + "]");
					logger.debug("[" + p.getName() + "] [" + p.getHttpUrl()
							+ "]");
				} else {
					System.out.println("[" + path + "]工程不是TS工程");
				}
				if (path.equals(projectPath)) {
					GitlabConnection connection = new GitlabConnection(host, p,
							session);
					return connection;
				}
			}
			// At this point the authentication was successful, but the
			// corresponding project
			// could not be found!
			throw new UnknownProjectException(projectPath);
		} catch (GitlabException e) {
			e.printStackTrace();
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			throw GitlabExceptionHandler.handle(e);
		} catch (Error e) {
			e.printStackTrace();
			throw GitlabExceptionHandler.handle(e);
		}
	}

	static GitlabConnection get(TaskRepository repository, boolean forceUpdate)
			throws GitlabException {
		try {
			String hash = constructURL(repository);
			System.out.println("获得处理后的URL[" + hash + "]");
			if (connections.containsKey(hash) && !forceUpdate) {
				return connections.get(hash);
			} else {
				GitlabConnection connection = validate(repository);
				connections.put(hash, connection);
				connection.update();
				return connection;
			}
		} catch (GitlabException e) {
			throw e;
		} catch (Exception e) {
			throw GitlabExceptionHandler.handle(e);
		} catch (Error e) {
			throw GitlabExceptionHandler.handle(e);
		}
	}

}
