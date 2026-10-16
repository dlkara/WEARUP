package com.lec.spring.controller;

import com.lec.spring.config.PrincipalUserDetails;
import com.lec.spring.domain.Post;
import com.lec.spring.domain.PostAttachment;
import com.lec.spring.domain.PostAttachmentValidator;
import com.lec.spring.domain.PostValidator;
import com.lec.spring.service.PostService;
import com.lec.spring.security.ResourceAccess;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/post")
public class PostController {
	
	private final PostService postService;
	private final PostAttachmentValidator postAttachmentValidator;
	private final ResourceAccess resourceAccess;
	
	public PostController(PostService postService, PostAttachmentValidator postAttachmentValidator, ResourceAccess resourceAccess) {
		this.postService = postService;
		this.postAttachmentValidator = postAttachmentValidator;
		this.resourceAccess = resourceAccess;
	}
	
	
	@InitBinder("post")
	public void initBinder1(WebDataBinder binder){
		// Associations and server-managed fields must never bind from a request.
		binder.setAllowedFields("id", "content", "items");
		binder.addValidators(new PostValidator());
	}
	
	@ModelAttribute("post")
	public Post getPost() {
		return new Post();
	}


	@RequestMapping("/list")
	public void list(Model model) {
		List<Post> posts = postService.list();
		model.addAttribute("posts", posts);
	}
	
	
	@GetMapping("/write")
	public void write(){}
	
	@PostMapping("/write")
	public String writeOk(
		 	@Valid Post post,
			BindingResult result,   // Validator 가 유효성 검사를 한 결과가 담긴 객체.
		    MultipartHttpServletRequest request,    // 첨부파일들 <name, file>
			Model model,    // 매개변수 선언시 BindingResult 보다 Model 을 뒤에 두어야 한다.
			RedirectAttributes redirectAttributes,  // redirect: 시 넘겨줄 값들.
			@AuthenticationPrincipal PrincipalUserDetails principal   // 로그인된 사용자 정보
	){
		// 1. 로그인 체크
		if (principal == null) {
			redirectAttributes.addFlashAttribute("error", "로그인 후 작성 가능합니다.");
			return "redirect:/post/list";
		}
		post.setUser(principal.getUser());
		
		// 2. PostValidator 수행 (자동)
		
		// 3. PostAttachmentValidator 수행 (파일별 수동 검증) : 이미지 최소 1개 업로드 검증
		//                                                      Map→List 로 변환해서 인덱스 접근 가능토록 함
		// 3-1) 업로드된 파일 전체 Map 으로 꺼내기
		Map<String, MultipartFile> files = request.getFileMap();
		
		// 3-2) 실제로 선택된(비어있지 않은) 파일만 골라내기
		List<MultipartFile> fileList = files.values().stream()
				.filter(f -> !f.isEmpty())
				.collect(Collectors.toList());
		
		// 3-3) 이미치 최소 1개 존재 여부 검증
		if (fileList.isEmpty()) {
			result.rejectValue("fileList", "1개 이상의 이미지를 등록해주세요.");
		}
		else {
			// 3-4) 파일 개별 검증 : 이미지 파일 여부 확인
			for (MultipartFile file : fileList) {
				postAttachmentValidator.validate(file, result);
			}
		}
		
		// 4. 검증 에러 처리 : validation 에러가 있었다면 redirect
		if(result.hasErrors()){
			// 에러 원인 콘솔에 출력
			showErrors(result);
			
			// 어떤 에러가 발생했는지 정보 전달
			for(FieldError err : result.getFieldErrors()){
				redirectAttributes.addFlashAttribute("error_" + err.getField(), err.getCode());
			}
			
			redirectAttributes.addFlashAttribute("user", post.getUser());
			redirectAttributes.addFlashAttribute("content", post.getContent());
			redirectAttributes.addFlashAttribute("items", post.getItems());
			redirectAttributes.addFlashAttribute("fileList", post.getFileList());
			
			return "redirect:/post/write";  // GET
		}
		
		// 5. 저장
		model.addAttribute("result", postService.write(post, files));
		return "post/writeOk";  // view
	}
	
	
	@RequestMapping("/detail/{id}")
	public String detail(@PathVariable Long id, Model model) {
		Post post = postService.detail(id);
		model.addAttribute("writerId", post.getUser().getId());
		model.addAttribute("writerUsername", post.getUser().getUsername());
		model.addAttribute("post", post);
		return "post/detail";
	}
	
	
	@GetMapping("/update/{id}")
	public String update(
			@PathVariable Long id,
			@ModelAttribute Post post,
			Model model,
			@AuthenticationPrincipal PrincipalUserDetails principal  // 작성자 검증용
	) {
		// 1) flash 에 없으면 DB 에서 로딩
		post = postService.detail(id);
		
		// 2) 작성자 권한 확인
		if (principal == null || !principal.getUser().getId().equals(post.getUser().getId())){
			return "redirect:/post/list";
		}
		
		model.addAttribute("post", post);
		return "post/update";
	}
	
	@PostMapping("/update")
	public String updateOk(
			@Valid @ModelAttribute("post") Post post,
			BindingResult result,
			MultipartHttpServletRequest request,
			Model model,
			@RequestParam(required = false) Long[] delFile     // 삭제될 파일들의 id(들)
	){
		// 1. 게시글 조회 : 존재하지 않는 게시글이면 redirect
		resourceAccess.requirePostOwner(post.getId());
		Post originalPost = postService.detail(post.getId());
		// Restore server-owned display fields only after binding and persisted ownership checks.
		post.setUser(originalPost.getUser());
		List<PostAttachment> deletions = resourceAccess.requirePostAttachments(post.getId(), delFile);
		delFile = deletions.stream().map(PostAttachment::getId).toArray(Long[]::new);

		// 2. 업로드된 파일 전체 Map 으로 꺼내기
		Map<String, MultipartFile> files = request.getFileMap();

		// 3. PostAttachmentValidator 수행 (파일별 수동 검증) : 이미지 최소 1개 업로드 검증
		//                                                      Map→List 로 변환해서 인덱스 접근 가능토록 함
		// 3-1) 실제로 선택된(비어있지 않은) 파일만 골라내기
		List<MultipartFile> fileList = files.values().stream()
				.filter(f -> !f.isEmpty())
				.collect(Collectors.toList());

		// 3-2) 기존 첨부파일의 개수
		int existingCnt = (originalPost.getFileList() == null) ? 0 : originalPost.getFileList().size();

		// 3-3) 삭제할 PostAttachment.id 의 개수
		int deleteCnt = (delFile == null) ? 0 : delFile.length;

		// 3-4) 새로 추가된 첨부파일의 개수
		List<MultipartFile> newFileList = files.values().stream()
				.filter(f -> !f.isEmpty())
				.collect(Collectors.toList());
		int newCnt = newFileList.size();

		// 3-5) 전체 남은 이미지 수 계산
		int totalImg = existingCnt - deleteCnt + newCnt;

		// 3-6) 이미지 최소 1개 존재 여부 검증
		if (totalImg < 1) {
			result.rejectValue("fileList", "1개 이상의 이미지를 등록해주세요.");
		}
		else {
			// 3-7) 파일 개별 검증 : 이미지 파일 여부 확인
			for (MultipartFile file : fileList) {
				postAttachmentValidator.validate(file, result);
			}
		}

		// 4. 검증 에러 처리 : validation 에러가 있었다면 redirect
		if(result.hasErrors()){
			showErrors(result);

			// 4-1) DB 첨부파일만 다시 세팅
			post.setFileList(postService.detail(post.getId()).getFileList());
			
			// 4-2) 삭제 대상 첨부파일 ID
			model.addAttribute("delFile", delFile);
			// validation 후에도 post 객체에 남은 content, items 그대로 보이도록 전달
			model.addAttribute("post", post);

			// 4-3) 에러 메시지 출력
			for(FieldError err : result.getFieldErrors()){
				model.addAttribute("error_" + err.getField(), err.getCode());
			}
			
			return "post/update";
		}
		
		// 5. 정상적으로 게시글 수정 완료
		int cnt = postService.update(post, files, delFile);
		model.addAttribute("result", cnt);
		return "post/updateOk";
	}
	
	
	@PostMapping("/delete")
	public String delete(
			Long id,
			@AuthenticationPrincipal PrincipalUserDetails principal,
			RedirectAttributes redirectAttributes,
			Model model
	){
		resourceAccess.requirePostOwner(id);

		int result = postService.deleteById(id);
		model.addAttribute("result", result);
		
		return "post/deleteOk";
	}
	
	
	// 바인딩 에러 출력 도우미 메소드
	public void showErrors(Errors errors){
		if (errors.hasErrors()) {
			log.warn("event=POST_VALIDATION_REJECTED errorCount={}", errors.getErrorCount());
		}
	} // end showErrors()

}
