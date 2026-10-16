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
		// - 요청으로 작성자 정보가 바뀌지 않도록 수정 가능한 필드만 허용
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
		// - 로그인 체크
		if (principal == null) {
			redirectAttributes.addFlashAttribute("error", "로그인 후 작성 가능합니다.");
			return "redirect:/post/list";
		}
		post.setUser(principal.getUser());
		
		// - PostValidator 수행 (자동)
		
		// - 파일 목록을 확인한 뒤 최소 이미지 수와 요청 MIME 검사
		// - 업로드된 파일 전체 Map 으로 꺼내기
		Map<String, MultipartFile> files = request.getFileMap();
		
		// - 실제로 선택된(비어있지 않은) 파일만 골라내기
		List<MultipartFile> fileList = files.values().stream()
				.filter(f -> !f.isEmpty())
				.collect(Collectors.toList());
		
		// - 등록할 이미지가 최소 1개 있는지 확인
		if (fileList.isEmpty()) {
			result.rejectValue("fileList", "1개 이상의 이미지를 등록해주세요.");
		}
		else {
			// - 파일별로 요청 MIME 허용 목록 확인
			for (MultipartFile file : fileList) {
				postAttachmentValidator.validate(file, result);
			}
		}
		
		// - 검증 오류가 있으면 작성 화면으로 이동
		if(result.hasErrors()){
			// - 검증 오류 개수만 로그에 기록
			showErrors(result);
			
			// - 어떤 에러가 발생했는지 정보 전달
			for(FieldError err : result.getFieldErrors()){
				redirectAttributes.addFlashAttribute("error_" + err.getField(), err.getCode());
			}
			
			redirectAttributes.addFlashAttribute("user", post.getUser());
			redirectAttributes.addFlashAttribute("content", post.getContent());
			redirectAttributes.addFlashAttribute("items", post.getItems());
			redirectAttributes.addFlashAttribute("fileList", post.getFileList());
			
			return "redirect:/post/write";  // GET
		}
		
		// - 저장
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
		// - 수정 화면에 표시할 게시글을 DB에서 조회
		post = postService.detail(id);
		
		// - 작성자 권한 확인
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
		// - DB 작성자 확인: 권한이 없으면 403, 게시글이 없으면 404로 종료
		resourceAccess.requirePostOwner(post.getId());
		Post originalPost = postService.detail(post.getId());
		// - 오류 폼에서도 작성자 비교와 수정 버튼이 유지되도록 DB 정보 복원
		post.setUser(originalPost.getUser());
		// - 변경 전에 삭제 대상 전체의 소속 확인과 중복 제거
		List<PostAttachment> deletions = resourceAccess.requirePostAttachments(post.getId(), delFile);
		delFile = deletions.stream().map(PostAttachment::getId).toArray(Long[]::new);

		// - 업로드된 파일 전체 Map 으로 꺼내기
		Map<String, MultipartFile> files = request.getFileMap();

		// - 파일 목록을 확인한 뒤 최소 이미지 수와 요청 MIME 검사
		// - 실제로 선택된(비어있지 않은) 파일만 골라내기
		List<MultipartFile> fileList = files.values().stream()
				.filter(f -> !f.isEmpty())
				.collect(Collectors.toList());

		// - 기존 첨부파일의 개수
		int existingCnt = (originalPost.getFileList() == null) ? 0 : originalPost.getFileList().size();

		// - 소속 확인·중복 제거를 마친 삭제 첨부 수
		int deleteCnt = (delFile == null) ? 0 : delFile.length;

		// - 새로 추가된 첨부파일의 개수
		List<MultipartFile> newFileList = files.values().stream()
				.filter(f -> !f.isEmpty())
				.collect(Collectors.toList());
		int newCnt = newFileList.size();

		// - 전체 남은 이미지 수 계산
		int totalImg = existingCnt - deleteCnt + newCnt;

		// - 이미지 최소 1개 존재 여부 검증
		if (totalImg < 1) {
			result.rejectValue("fileList", "1개 이상의 이미지를 등록해주세요.");
		}
		else {
			// - 파일별로 요청 MIME 허용 목록 확인
			for (MultipartFile file : fileList) {
				postAttachmentValidator.validate(file, result);
			}
		}

		// - 검증 오류가 있으면 수정 폼을 다시 표시
		if(result.hasErrors()){
			showErrors(result);

			// - DB 첨부파일만 다시 세팅
			post.setFileList(postService.detail(post.getId()).getFileList());
			
			// - 삭제 대상 첨부파일 ID
			model.addAttribute("delFile", delFile);
			// - 검증 오류가 나도 입력한 본문과 상품 목록은 유지
			model.addAttribute("post", post);

			// - 에러 메시지 출력
			for(FieldError err : result.getFieldErrors()){
				model.addAttribute("error_" + err.getField(), err.getCode());
			}
			
			return "post/update";
		}
		
		// - 검증을 통과하면 서비스에서 게시글 수정
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
	
	
	// - 바인딩 에러 출력 도우미 메소드
	public void showErrors(Errors errors){
		if (errors.hasErrors()) {
			log.warn("event=POST_VALIDATION_REJECTED errorCount={}", errors.getErrorCount());
		}
	}

}
