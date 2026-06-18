package com.testdatagen.service;

import com.testdatagen.model.entity.SqlScript;
import com.testdatagen.repository.SqlScriptRepository;
import com.testdatagen.security.CurrentUserContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SqlScriptService {

    private final SqlScriptRepository sqlScriptRepository;

    public SqlScriptService(SqlScriptRepository sqlScriptRepository) {
        this.sqlScriptRepository = sqlScriptRepository;
    }

    public List<SqlScript> listAll() {
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            return sqlScriptRepository.findAllByOrderByUpdatedAtDesc();
        }
        if (CurrentUserContext.isAdmin()) {
            return sqlScriptRepository.findAllByOrderByUpdatedAtDesc();
        }
        return sqlScriptRepository.findAllByUserIdOrUserIdIsNullOrderByUpdatedAtDesc(userId);
    }

    public SqlScript getById(Long id) {
        return sqlScriptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("SQL脚本不存在: id=" + id));
    }

    public SqlScript create(String name, String sqlContent, String description, Long connectionId) {
        SqlScript script = new SqlScript();
        script.setName(name);
        script.setSqlContent(sqlContent);
        script.setDescription(description);
        script.setConnectionId(connectionId);
        script.setUserId(CurrentUserContext.getUserId());
        return sqlScriptRepository.save(script);
    }

    public SqlScript update(Long id, String name, String sqlContent, String description) {
        SqlScript script = getById(id);
        if (name != null) {
            script.setName(name);
        }
        if (sqlContent != null) {
            script.setSqlContent(sqlContent);
        }
        if (description != null) {
            script.setDescription(description);
        }
        return sqlScriptRepository.save(script);
    }

    public void delete(Long id) {
        sqlScriptRepository.deleteById(id);
    }
}
