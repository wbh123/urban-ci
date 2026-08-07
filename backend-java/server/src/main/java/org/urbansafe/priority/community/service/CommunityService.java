package org.urbansafe.priority.community.service;

import java.util.UUID;
import org.urbansafe.priority.community.command.CreateCommunityCommand;
import org.urbansafe.priority.community.command.UpdateCommunityCommand;
import org.urbansafe.priority.community.result.CommunityDetailResult;
import org.urbansafe.priority.common.pagination.ApiPageRequest;
import org.urbansafe.priority.common.pagination.PageResult;
import org.urbansafe.priority.community.result.CommunityListResult;

public interface CommunityService {

    CommunityDetailResult create(CreateCommunityCommand command);

    PageResult<CommunityListResult> page(String keyword, String administrativeRegion, String status,
                                         ApiPageRequest pageRequest, String sort);

    CommunityDetailResult get(UUID communityId);

    CommunityDetailResult update(UUID communityId, UpdateCommunityCommand command);

    void delete(UUID communityId);
}
