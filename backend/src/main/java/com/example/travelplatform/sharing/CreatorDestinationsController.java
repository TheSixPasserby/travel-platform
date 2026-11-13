package com.example.travelplatform.sharing;
import com.example.travelplatform.common.api.*;
import com.example.travelplatform.common.validation.RequestValues;
import com.example.travelplatform.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class CreatorDestinationsController {
    private final PublicContentMapper mapper;
    private final UserService users;
    public CreatorDestinationsController(PublicContentMapper mapper,UserService users) {this.mapper=mapper;this.users=users;}
    public record DestinationVO(String id,String name,String destinationType,String coverImageUrl,long guideCount) { }
    @GetMapping("/api/v1/users/by-username/{username}/destinations") @SecurityRequirements
    @Operation(summary="创作者公开手记关联目的地分页")
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ApiResponse<PageResponse<DestinationVO>> list(@PathVariable String username,@RequestParam(required=false) String page,@RequestParam(required=false) String size) {
        long author=users.publicUserByUsername(username).getId(),current=RequestValues.page(page),limit=RequestValues.size(size),offset=RequestValues.offset(current,limit);
        long total=mapper.countDestinations(author);
        var rows=offset>=total?List.<PublicContentMapper.DestinationRow>of():mapper.destinations(author,limit,offset);
        var values=rows.stream().map(d->new DestinationVO(Long.toString(d.id()),d.name(),d.destinationType(),d.coverImageUrl(),d.guideCount())).toList();
        return ApiResponse.success(new PageResponse<>(values,total,current,limit,total/limit+(total%limit==0?0:1)));
    }
}
